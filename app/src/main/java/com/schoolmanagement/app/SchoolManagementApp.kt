package com.schoolmanagement.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class SchoolManagementApp : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        FirebaseCrashlytics.getInstance().setCustomKey(CRASHLYTICS_KEY_BUILD_FLAVOR, BuildConfig.APP_ENV)
        FirebaseCrashlytics.getInstance().setCustomKey(CRASHLYTICS_KEY_USER_ROLE, UNKNOWN_VALUE)
        FirebaseCrashlytics.getInstance().setCustomKey(CRASHLYTICS_KEY_SCHOOL_ID, UNKNOWN_VALUE)

        applyUserCrashlyticsContext()
        FcmTokenSyncManager.syncCurrentTokenToProfile()
    }

    private fun applyUserCrashlyticsContext() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseCrashlytics.getInstance().setUserId(user.uid)

        FirebaseFirestore.getInstance()
            .collection(USERS_COLLECTION)
            .document(user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val userRole = snapshot.getString(FIELD_ROLE) ?: UNKNOWN_VALUE
                val schoolId = snapshot.getString(FIELD_SCHOOL_ID) ?: UNKNOWN_VALUE

                FirebaseCrashlytics.getInstance().setCustomKey(CRASHLYTICS_KEY_USER_ROLE, userRole)
                FirebaseCrashlytics.getInstance().setCustomKey(CRASHLYTICS_KEY_SCHOOL_ID, schoolId)
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Unable to load user profile for Crashlytics context", throwable)
                FirebaseCrashlytics.getInstance().recordException(throwable)
            }
    }

    companion object {
        private const val TAG = "SchoolManagementApp"
        private const val USERS_COLLECTION = "users"
        private const val FIELD_ROLE = "role"
        private const val FIELD_SCHOOL_ID = "schoolId"

        private const val CRASHLYTICS_KEY_USER_ROLE = "user_role"
        private const val CRASHLYTICS_KEY_SCHOOL_ID = "school_id"
        private const val CRASHLYTICS_KEY_BUILD_FLAVOR = "build_flavor"
        private const val UNKNOWN_VALUE = "unknown"
    }
}

internal object FcmTokenSyncManager {
    private const val TAG = "FcmTokenSyncManager"
    private const val USERS_COLLECTION = "users"
    private const val FIELD_FCM_TOKEN = "fcmToken"
    private const val FIELD_FCM_UPDATED_AT = "fcmTokenUpdatedAt"

    fun syncCurrentTokenToProfile() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                syncTokenToUserProfile(token)
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to fetch FCM token", throwable)
                FirebaseCrashlytics.getInstance().recordException(throwable)
            }
    }

    fun syncTokenToUserProfile(token: String) {
        if (token.isBlank()) {
            return
        }

        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Log.d(TAG, "Skipping FCM token sync because user is not authenticated")
            return
        }

        val profileUpdate = mapOf(
            FIELD_FCM_TOKEN to token,
            FIELD_FCM_UPDATED_AT to com.google.firebase.Timestamp.now()
        )

        FirebaseFirestore.getInstance()
            .collection(USERS_COLLECTION)
            .document(user.uid)
            .set(profileUpdate, com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to sync FCM token to Firestore", throwable)
                FirebaseCrashlytics.getInstance().recordException(throwable)
            }
    }
}
