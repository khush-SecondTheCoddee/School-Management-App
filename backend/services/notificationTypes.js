const NotificationTypes = Object.freeze({
  ANNOUNCEMENT: 'announcement',
  HOMEWORK: 'homework',
  RESULTS: 'results',
  ATTENDANCE_ALERT: 'attendance_alert',
  FEE_REMINDER: 'fee_reminder',
});

const TopicScopes = Object.freeze({
  SCHOOL: 'school',
  CLASS: 'class',
  ROLE: 'role',
  USER: 'user',
});

function buildTopic({ schoolId, classId, role }) {
  const topics = [];

  if (schoolId) {
    topics.push(`${TopicScopes.SCHOOL}_${schoolId}`);
  }

  if (classId) {
    topics.push(`${TopicScopes.CLASS}_${classId}`);
  }

  if (role) {
    topics.push(`${TopicScopes.ROLE}_${String(role).toLowerCase()}`);
  }

  return topics;
}

function buildUserTokenTarget(uid) {
  if (!uid) {
    return null;
  }

  return `${TopicScopes.USER}_${uid}`;
}

module.exports = {
  NotificationTypes,
  TopicScopes,
  buildTopic,
  buildUserTokenTarget,
};
