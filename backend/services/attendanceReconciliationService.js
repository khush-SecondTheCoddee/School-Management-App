const admin = require('firebase-admin');
const { sendAttendanceNotification } = require('./notificationService');

const DEFAULT_LATE_AFTER_MINUTES = 15;
const DEFAULT_SCHOOL_START_HOUR = 9;

function toDateKey(date) {
  return date.toISOString().slice(0, 10);
}

function toMinutesSinceMidnight(date) {
  return date.getUTCHours() * 60 + date.getUTCMinutes();
}

async function findUserByBiometricId(biometricId) {
  const userSnap = await admin
    .firestore()
    .collection('users')
    .where('biometricId', '==', biometricId)
    .limit(1)
    .get();

  if (userSnap.empty) {
    return null;
  }

  const doc = userSnap.docs[0];
  return { uid: doc.id, ...doc.data() };
}

async function queueUnmatchedEvent(eventDoc) {
  await admin.firestore().collection('attendanceReconciliationQueue').add({
    biometricId: eventDoc.biometricId,
    eventId: eventDoc.id,
    timestamp: eventDoc.timestamp,
    deviceId: eventDoc.deviceId,
    source: eventDoc.source,
    reason: 'USER_NOT_MAPPED_TO_BIOMETRIC_ID',
    status: 'pending',
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

async function reconcileAndBuildDailyAttendance(normalizedEvents) {
  const db = admin.firestore();
  const groupedByUserDate = new Map();
  let unmatchedCount = 0;

  for (const event of normalizedEvents) {
    const user = await findUserByBiometricId(event.biometricId);

    if (!user) {
      unmatchedCount += 1;
      await queueUnmatchedEvent(event);
      continue;
    }

    const date = new Date(event.timestampIso);
    const dateKey = toDateKey(date);
    const groupingKey = `${user.uid}_${dateKey}`;

    if (!groupedByUserDate.has(groupingKey)) {
      groupedByUserDate.set(groupingKey, {
        dateKey,
        user,
        inTimes: [],
        outTimes: [],
      });
    }

    const group = groupedByUserDate.get(groupingKey);
    const eventTime = date.toISOString();
    if (event.direction === 'in') {
      group.inTimes.push(eventTime);
    } else {
      group.outTimes.push(eventTime);
    }
  }

  const attendanceDocs = [];

  for (const group of groupedByUserDate.values()) {
    group.inTimes.sort();
    group.outTimes.sort();

    const firstIn = group.inTimes[0] || null;
    const lastOut = group.outTimes[group.outTimes.length - 1] || null;

    let status = 'absent';
    if (firstIn) {
      const schoolStartMinutes = DEFAULT_SCHOOL_START_HOUR * 60 + DEFAULT_LATE_AFTER_MINUTES;
      const firstInMinutes = toMinutesSinceMidnight(new Date(firstIn));
      status = firstInMinutes > schoolStartMinutes ? 'late' : 'present';
    }

    const docId = `${group.user.uid}_${group.dateKey}`;
    const docData = {
      uid: group.user.uid,
      classId: group.user.classId || 'unknown-class',
      schoolId: group.user.schoolId || null,
      date: group.dateKey,
      status,
      firstIn,
      lastOut,
      source: 'biometric',
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await db.collection('dailyAttendance').doc(docId).set(docData, { merge: true });
    attendanceDocs.push(docData);

    if (status === 'absent' || status === 'late') {
      await sendAttendanceNotification({ user: group.user, status, dateKey: group.dateKey });
    }
  }

  return {
    attendanceUpdated: attendanceDocs.length,
    unmatchedQueued: unmatchedCount,
    attendanceDocs,
  };
}

module.exports = {
  reconcileAndBuildDailyAttendance,
};
