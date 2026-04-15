const admin = require('firebase-admin');

async function sendAttendanceNotification({ user, status, dateKey }) {
  const fcmTokens = new Set();

  if (user.fcmToken) {
    fcmTokens.add(user.fcmToken);
  }

  const parentIds = Array.isArray(user.parentIds) ? user.parentIds : [];
  if (parentIds.length) {
    const parentSnaps = await Promise.all(
      parentIds.map((parentId) => admin.firestore().collection('users').doc(parentId).get())
    );

    parentSnaps.forEach((snap) => {
      if (snap.exists) {
        const token = snap.data()?.fcmToken;
        if (token) {
          fcmTokens.add(token);
        }
      }
    });
  }

  if (!fcmTokens.size) {
    return { sent: 0, reason: 'No FCM tokens found for student/parents' };
  }

  const message = {
    notification: {
      title: 'Attendance Alert',
      body: `${user.displayName || 'Student'} marked ${status.toUpperCase()} on ${dateKey}`,
    },
    data: {
      type: 'attendance_status',
      status,
      date: dateKey,
      studentUid: user.uid,
    },
    tokens: Array.from(fcmTokens),
  };

  const response = await admin.messaging().sendEachForMulticast(message);

  return {
    sent: response.successCount,
    failed: response.failureCount,
  };
}

module.exports = {
  sendAttendanceNotification,
};
