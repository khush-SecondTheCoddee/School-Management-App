package com.schoolmanagement.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class SchoolMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenSyncManager.syncTokenToUserProfile(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val route = message.data["route"] ?: "notifications/inbox"
        NotificationRouteStore.latestRoute = route
    }
}

object NotificationRouteStore {
    var latestRoute: String? = null
}
