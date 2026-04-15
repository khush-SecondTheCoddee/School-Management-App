const admin = require('firebase-admin');

const { enqueueNotification } = require('./notificationDispatcherService');
const { NotificationTypes } = require('./notificationTypes');

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

  const userIds = [user.uid, ...parentIds].filter(Boolean);

  if (!fcmTokens.size && !userIds.length) {
    return { sent: 0, reason: 'No FCM tokens found for student/parents' };
  }

  const enqueueResult = await enqueueNotification({
    type: NotificationTypes.ATTENDANCE_ALERT,
    locale: user.preferredLocale || 'en',
    templateContext: {
      studentName: user.displayName || 'Student',
      status: status.toUpperCase(),
      date: dateKey,
    },
    target: {
      schoolId: user.schoolId,
      classId: user.classId,
      role: 'parent',
      userIds,
      tokens: Array.from(fcmTokens),
    },
    data: {
      status,
      date: dateKey,
      studentUid: user.uid,
    },
    route: `attendance/${user.uid}/${dateKey}`,
  });

  return {
    queued: 1,
    jobId: enqueueResult.jobId,
    targetedUsers: userIds.length,
    directTokens: fcmTokens.size,
  };
}

module.exports = {
  sendAttendanceNotification,
};
