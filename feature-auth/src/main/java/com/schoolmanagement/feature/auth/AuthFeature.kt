package com.schoolmanagement.feature.auth

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.util.PatternsCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

private const val USERS_COLLECTION = "users"
private const val MUST_CHANGE_PASSWORD_FIELD = "mustChangePassword"
private const val PHONE_COMPAT_USER = "phone@phone.co"

class AuthSessionStorage(context: Context) {
    private val appContext = context.applicationContext

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        "auth_secure_store",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setAuthenticated(isAuthenticated: Boolean) {
        prefs.edit().putBoolean(KEY_IS_AUTHENTICATED, isAuthenticated).apply()
    }

    fun isAuthenticated(): Boolean = prefs.getBoolean(KEY_IS_AUTHENTICATED, false)

    fun setMustChangePassword(mustChange: Boolean) {
        prefs.edit().putBoolean(KEY_MUST_CHANGE_PASSWORD, mustChange).apply()
    }

    fun mustChangePassword(): Boolean = prefs.getBoolean(KEY_MUST_CHANGE_PASSWORD, false)

    fun setIdToken(token: String) {
        prefs.edit().putString(KEY_ID_TOKEN, token).apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_IS_AUTHENTICATED = "isAuthenticated"
        private const val KEY_MUST_CHANGE_PASSWORD = "mustChangePassword"
        private const val KEY_ID_TOKEN = "idToken"
    }
}

sealed interface LoginRequest {
    data class EmailPassword(val email: String, val password: String) : LoginRequest
    data class PhoneOtp(val phone: String) : LoginRequest
}

sealed interface LoginResolverResult {
    data class Resolved(val request: LoginRequest) : LoginResolverResult
    data class Rejected(val message: String) : LoginResolverResult
}

object LoginResolver {
    fun resolve(identifier: String, password: String): LoginResolverResult {
        val input = identifier.trim()
        if (PatternsCompat.EMAIL_ADDRESS.matcher(input).matches()) {
            return LoginResolverResult.Resolved(
                LoginRequest.EmailPassword(email = input, password = password)
            )
        }

        val normalizedPhone = normalizePhone(input)
        if (normalizedPhone != null) {
            return LoginResolverResult.Resolved(LoginRequest.PhoneOtp(phone = normalizedPhone))
        }

        return LoginResolverResult.Rejected("Use a valid email or phone number")
    }

    private fun normalizePhone(raw: String): String? {
        val cleaned = raw.replace(" ", "").replace("-", "")
        val regex = Regex("^\\+?[1-9]\\d{7,14}$")
        return if (regex.matches(cleaned)) {
            if (cleaned.startsWith("+")) cleaned else "+$cleaned"
        } else {
            null
        }
    }
}

class AuthBackendAdapter(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: AuthSessionStorage
) {
    private var verificationId: String? = null

    fun restoreSession(onDone: (Boolean, Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            storage.clearSession()
            onDone(false, false)
            return
        }

        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            storage.setIdToken(tokenResult.token.orEmpty())
            storage.setAuthenticated(true)
            refreshMustChangePassword(onDone = onDone)
        }.addOnFailureListener {
            storage.clearSession()
            onDone(false, false)
        }
    }

    fun login(
        activity: Activity,
        identifier: String,
        password: String,
        onOtpRequired: () -> Unit,
        onError: (String) -> Unit,
        onSuccess: (mustChangePassword: Boolean) -> Unit
    ) {
        when (val resolved = LoginResolver.resolve(identifier = identifier, password = password)) {
            is LoginResolverResult.Rejected -> onError(resolved.message)
            is LoginResolverResult.Resolved -> when (val request = resolved.request) {
                is LoginRequest.EmailPassword -> loginWithEmail(
                    email = request.email,
                    password = request.password,
                    onError = onError,
                    onSuccess = onSuccess
                )

                is LoginRequest.PhoneOtp -> startPhoneOtp(
                    activity = activity,
                    phone = request.phone,
                    onOtpRequired = onOtpRequired,
                    onError = onError,
                    onSuccess = onSuccess
                )
            }
        }
    }

    fun verifyOtp(
        otp: String,
        onError: (String) -> Unit,
        onSuccess: (mustChangePassword: Boolean) -> Unit
    ) {
        val id = verificationId
        if (id.isNullOrBlank()) {
            onError("Start phone verification first")
            return
        }

        val credential = PhoneAuthProvider.getCredential(id, otp.trim())
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                persistSessionAndLoadFlags(onSuccess, onError)
            }
            .addOnFailureListener { onError(it.message ?: "OTP verification failed") }
    }

    fun forgotPassword(email: String, onDone: (String) -> Unit) {
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onDone("Reset email sent") }
            .addOnFailureListener { onDone(it.message ?: "Unable to send reset email") }
    }

    fun changePassword(newPassword: String, onDone: (String, Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onDone("Not signed in", false)
            return
        }

        user.updatePassword(newPassword)
            .addOnSuccessListener {
                firestore.collection(USERS_COLLECTION)
                    .document(user.uid)
                    .update(MUST_CHANGE_PASSWORD_FIELD, false)
                    .addOnSuccessListener {
                        storage.setMustChangePassword(false)
                        onDone("Password changed", true)
                    }
                    .addOnFailureListener {
                        storage.setMustChangePassword(false)
                        onDone("Password changed", true)
                    }
            }
            .addOnFailureListener {
                onDone(it.message ?: "Failed to change password", false)
            }
    }

    fun logout() {
        auth.signOut()
        storage.clearSession()
    }

    private fun loginWithEmail(
        email: String,
        password: String,
        onError: (String) -> Unit,
        onSuccess: (mustChangePassword: Boolean) -> Unit
    ) {
        val backendEmail = if (email == PHONE_COMPAT_USER) {
            PHONE_COMPAT_USER
        } else {
            email
        }

        auth.signInWithEmailAndPassword(backendEmail, password)
            .addOnSuccessListener {
                persistSessionAndLoadFlags(onSuccess, onError)
            }
            .addOnFailureListener {
                onError(it.message ?: "Login failed")
            }
    }

    private fun startPhoneOtp(
        activity: Activity,
        phone: String,
        onOtpRequired: () -> Unit,
        onError: (String) -> Unit,
        onSuccess: (mustChangePassword: Boolean) -> Unit
    ) {
        val callback = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { persistSessionAndLoadFlags(onSuccess, onError) }
                    .addOnFailureListener { onError(it.message ?: "Phone authentication failed") }
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                onError(exception.message ?: "OTP request failed")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                this@AuthBackendAdapter.verificationId = verificationId
                onOtpRequired()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callback)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun persistSessionAndLoadFlags(
        onSuccess: (mustChangePassword: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onError("Authenticated user not found")
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                storage.setIdToken(tokenResult.token.orEmpty())
                storage.setAuthenticated(true)
                refreshMustChangePassword { isAuthenticated, mustChangePassword ->
                    if (!isAuthenticated) {
                        onError("Session could not be restored")
                    } else {
                        onSuccess(mustChangePassword)
                    }
                }
            }
            .addOnFailureListener { onError(it.message ?: "Could not load session token") }
    }

    private fun refreshMustChangePassword(onDone: (Boolean, Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            storage.clearSession()
            onDone(false, false)
            return
        }

        firestore.collection(USERS_COLLECTION)
            .document(user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val mustChange = snapshot.getBoolean(MUST_CHANGE_PASSWORD_FIELD) ?: false
                storage.setMustChangePassword(mustChange)
                onDone(true, mustChange)
            }
            .addOnFailureListener {
                val cachedValue = storage.mustChangePassword()
                onDone(true, cachedValue)
            }
    }
}

enum class AuthScreen {
    Login,
    Otp,
    ForgotPassword,
    ChangePassword,
    Logout
}

@Composable
fun AuthGate(
    modifier: Modifier = Modifier,
    adapter: AuthBackendAdapter,
    content: @Composable (onLogout: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }

    var isBootstrapping by remember { mutableStateOf(true) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var mustChangePassword by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(AuthScreen.Login) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        adapter.restoreSession { loggedIn, mustChange ->
            isAuthenticated = loggedIn
            mustChangePassword = mustChange
            screen = when {
                !loggedIn -> AuthScreen.Login
                mustChange -> AuthScreen.ChangePassword
                else -> AuthScreen.Logout
            }
            isBootstrapping = false
        }
    }

    if (isBootstrapping) {
        FullScreenMessage("Checking session…")
        return
    }

    if (isAuthenticated && !mustChangePassword) {
        content {
            adapter.logout()
            isAuthenticated = false
            mustChangePassword = false
            screen = AuthScreen.Login
        }
        return
    }

    when (screen) {
        AuthScreen.Login -> LoginScreen(
            modifier = modifier,
            onLogin = { identifier, password ->
                val safeActivity = activity
                if (safeActivity == null) {
                    message = "Phone OTP requires an activity context"
                    return@LoginScreen
                }

                adapter.login(
                    activity = safeActivity,
                    identifier = identifier,
                    password = password,
                    onOtpRequired = {
                        screen = AuthScreen.Otp
                        message = "OTP sent"
                    },
                    onError = { message = it },
                    onSuccess = { mustChange ->
                        isAuthenticated = true
                        mustChangePassword = mustChange
                        screen = if (mustChange) AuthScreen.ChangePassword else AuthScreen.Logout
                        message = "Welcome"
                    }
                )
            },
            onForgotPassword = { screen = AuthScreen.ForgotPassword },
            message = message
        )

        AuthScreen.Otp -> OtpVerifyScreen(
            modifier = modifier,
            onVerify = { otp ->
                adapter.verifyOtp(
                    otp = otp,
                    onError = { message = it },
                    onSuccess = { mustChange ->
                        isAuthenticated = true
                        mustChangePassword = mustChange
                        screen = if (mustChange) AuthScreen.ChangePassword else AuthScreen.Logout
                        message = "Signed in"
                    }
                )
            },
            onBack = { screen = AuthScreen.Login },
            message = message
        )

        AuthScreen.ForgotPassword -> ForgotPasswordScreen(
            modifier = modifier,
            onSend = { email ->
                adapter.forgotPassword(email) { response ->
                    message = response
                    screen = AuthScreen.Login
                }
            },
            onBack = { screen = AuthScreen.Login },
            message = message
        )

        AuthScreen.ChangePassword -> ChangePasswordScreen(
            modifier = modifier,
            onChangePassword = { password ->
                adapter.changePassword(password) { response, success ->
                    message = response
                    if (success) {
                        mustChangePassword = false
                        screen = AuthScreen.Logout
                    }
                }
            },
            message = message
        )

        AuthScreen.Logout -> LogoutScreen(
            modifier = modifier,
            onLogout = {
                adapter.logout()
                isAuthenticated = false
                mustChangePassword = false
                screen = AuthScreen.Login
            }
        )
    }
}

@Composable
private fun LoginScreen(
    modifier: Modifier,
    onLogin: (String, String) -> Unit,
    onForgotPassword: () -> Unit,
    message: String
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthColumn(modifier = modifier) {
        Text("Login", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text("Email or phone") }
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Button(onClick = { onLogin(identifier, password) }, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
        Button(onClick = onForgotPassword, modifier = Modifier.fillMaxWidth()) {
            Text("Forgot password")
        }
        if (message.isNotBlank()) Text(message)
    }
}

@Composable
private fun OtpVerifyScreen(
    modifier: Modifier,
    onVerify: (String) -> Unit,
    onBack: () -> Unit,
    message: String
) {
    var otp by remember { mutableStateOf("") }

    AuthColumn(modifier = modifier) {
        Text("Verify OTP", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = otp,
            onValueChange = { otp = it },
            label = { Text("OTP") }
        )
        Button(onClick = { onVerify(otp) }, modifier = Modifier.fillMaxWidth()) {
            Text("Verify")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
        if (message.isNotBlank()) Text(message)
    }
}

@Composable
private fun ForgotPasswordScreen(
    modifier: Modifier,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    message: String
) {
    var email by remember { mutableStateOf("") }

    AuthColumn(modifier = modifier) {
        Text("Forgot Password", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") }
        )
        Button(onClick = { onSend(email) }, modifier = Modifier.fillMaxWidth()) {
            Text("Send reset link")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
        if (message.isNotBlank()) Text(message)
    }
}

@Composable
private fun ChangePasswordScreen(
    modifier: Modifier,
    onChangePassword: (String) -> Unit,
    message: String
) {
    var password by remember { mutableStateOf("") }

    AuthColumn(modifier = modifier) {
        Text("Change Password", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = password,
            onValueChange = { password = it },
            label = { Text("New password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Button(onClick = { onChangePassword(password) }, modifier = Modifier.fillMaxWidth()) {
            Text("Update password")
        }
        if (message.isNotBlank()) Text(message)
    }
}

@Composable
private fun LogoutScreen(
    modifier: Modifier,
    onLogout: () -> Unit
) {
    AuthColumn(modifier = modifier) {
        Text("Session", style = MaterialTheme.typography.headlineSmall)
        Text("You are signed in")
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }
    }
}

@Composable
private fun FullScreenMessage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(message, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun AuthColumn(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}
