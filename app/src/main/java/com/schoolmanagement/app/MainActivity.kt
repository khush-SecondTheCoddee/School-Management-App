package com.schoolmanagement.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.schoolmanagement.app.ui.designsystem.SchoolTheme
import com.schoolmanagement.app.ui.navigation.SchoolRootApp
import com.schoolmanagement.feature.auth.AuthBackendAdapter
import com.schoolmanagement.feature.auth.AuthGate
import com.schoolmanagement.feature.auth.AuthSessionStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SchoolTheme {
                val adapter = remember {
                    AuthBackendAdapter(
                        auth = FirebaseAuth.getInstance(),
                        firestore = FirebaseFirestore.getInstance(),
                        storage = AuthSessionStorage(applicationContext)
                    )
                }
                AuthGate(adapter = adapter) {
                    SchoolRootApp()
                }
            }
        }
    }
}
