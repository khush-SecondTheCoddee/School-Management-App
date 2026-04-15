package com.schoolmanagement.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant

enum class NotificationType {
    Announcement,
    Homework,
    Results,
    AttendanceAlert,
    FeeReminder,
}

data class NotificationInboxItem(
    val id: String,
    val title: String,
    val message: String,
    val route: String,
    val type: NotificationType,
    val createdAtEpochMs: Long,
    val isRead: Boolean,
)

@Composable
fun NotificationsInbox(
    initialItems: List<NotificationInboxItem>,
    onDeepLinkRoute: (String) -> Unit,
) {
    val inboxItems = remember(initialItems) { mutableStateListOf(*initialItems.toTypedArray()) }
    val unreadCount = inboxItems.count { !it.isRead }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Inbox · Unread $unreadCount",
            style = MaterialTheme.typography.titleMedium,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(inboxItems, key = { it.id }) { item ->
                NotificationCard(item = item) {
                    val index = inboxItems.indexOfFirst { it.id == item.id }
                    if (index >= 0) {
                        inboxItems[index] = inboxItems[index].copy(isRead = true)
                    }
                    onDeepLinkRoute(item.route)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationInboxItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = item.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (item.isRead) "Read" else "Unread",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Text(text = item.message, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${item.type} • ${Instant.ofEpochMilli(item.createdAtEpochMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

object NotificationsFeature {
    fun sampleInbox(): List<NotificationInboxItem> = listOf(
        NotificationInboxItem(
            id = "a1",
            title = "Final exam starts next week",
            message = "Review the exam timetable and seating plan.",
            route = "results/exam-schedule",
            type = NotificationType.Announcement,
            createdAtEpochMs = System.currentTimeMillis() - 7_200_000,
            isRead = false,
        ),
        NotificationInboxItem(
            id = "a2",
            title = "Math homework due",
            message = "Chapter 8 worksheet is due tomorrow.",
            route = "homework/math/chapter-8",
            type = NotificationType.Homework,
            createdAtEpochMs = System.currentTimeMillis() - 86_400_000,
            isRead = false,
        ),
        NotificationInboxItem(
            id = "a3",
            title = "Fee reminder",
            message = "Term 2 tuition fee is due on Friday.",
            route = "finance/fees/term-2",
            type = NotificationType.FeeReminder,
            createdAtEpochMs = System.currentTimeMillis() - 172_800_000,
            isRead = true,
        ),
    )
}
