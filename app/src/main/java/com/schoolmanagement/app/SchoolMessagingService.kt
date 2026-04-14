package com.schoolmanagement.app

import com.google.firebase.messaging.FirebaseMessagingService

class SchoolMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenSyncManager.syncTokenToUserProfile(token)
    }
}
