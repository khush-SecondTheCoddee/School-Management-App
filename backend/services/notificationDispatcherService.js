const admin = require('firebase-admin');

const { buildLocalizedPayload } = require('./notificationTemplates');
const { buildTopic } = require('./notificationTypes');

const DEFAULT_MAX_RETRIES = 3;

function notificationJobsCollection() {
  return admin.firestore().collection('notification_jobs');
}

function notificationErrorsCollection() {
  return admin.firestore().collection('notification_error_queue');
}

function inAppInboxCollection(uid) {
  return admin.firestore().collection('users').doc(uid).collection('inbox');
}

async function enqueueNotification(job) {
  const now = admin.firestore.FieldValue.serverTimestamp();
  const created = await notificationJobsCollection().add({
    ...job,
    status: 'queued',
    retries: 0,
    maxRetries: job.maxRetries || DEFAULT_MAX_RETRIES,
    createdAt: now,
    updatedAt: now,
  });

  return { jobId: created.id };
}

function buildTargetPlan(target = {}) {
  const topics = [
    ...(Array.isArray(target.topics) ? target.topics : []),
    ...buildTopic({
      schoolId: target.schoolId,
      classId: target.classId,
      role: target.role,
    }),
  ];

  return {
    topics: Array.from(new Set(topics)),
    tokens: Array.from(new Set(Array.isArray(target.tokens) ? target.tokens : [])),
    userIds: Array.isArray(target.userIds) ? target.userIds : [],
  };
}

async function sendToUserInbox(userIds, payload) {
  if (!Array.isArray(userIds) || !userIds.length) {
    return;
  }

  const now = admin.firestore.FieldValue.serverTimestamp();
  const writes = userIds.map((uid) =>
    inAppInboxCollection(uid).add({
      ...payload,
      readAt: null,
      isRead: false,
      createdAt: now,
    })
  );

  await Promise.all(writes);
}

async function dispatchSingleJob(jobSnap) {
  const job = jobSnap.data();
  const jobRef = jobSnap.ref;

  const localizedPayload = buildLocalizedPayload({
    type: job.type,
    locale: job.locale,
    context: job.templateContext,
    overrides: job.overrides,
  });

  const targetPlan = buildTargetPlan(job.target);
  const dataPayload = {
    ...job.data,
    type: localizedPayload.type,
    i18nKey: localizedPayload.i18nKey,
    route: job.route || 'notifications/inbox',
  };

  const topicPromises = targetPlan.topics.map((topic) =>
    admin.messaging().send({
      topic,
      notification: {
        title: localizedPayload.title,
        body: localizedPayload.body,
      },
      data: dataPayload,
    })
  );

  let multicastResponse = { successCount: 0, failureCount: 0 };
  if (targetPlan.tokens.length) {
    multicastResponse = await admin.messaging().sendEachForMulticast({
      tokens: targetPlan.tokens,
      notification: {
        title: localizedPayload.title,
        body: localizedPayload.body,
      },
      data: dataPayload,
    });
  }

  await Promise.all(topicPromises);
  await sendToUserInbox(targetPlan.userIds, {
    ...localizedPayload,
    route: job.route || 'notifications/inbox',
    data: job.data || {},
  });

  await jobRef.update({
    status: 'sent',
    sentAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    delivery: {
      topics: targetPlan.topics.length,
      tokenSuccess: multicastResponse.successCount,
      tokenFailure: multicastResponse.failureCount,
      inboxRecipients: targetPlan.userIds.length,
    },
  });

  return { jobId: jobSnap.id, sent: true };
}

async function moveToErrorQueue(jobSnap, errorMessage) {
  const job = jobSnap.data();
  await notificationErrorsCollection().add({
    ...job,
    sourceJobId: jobSnap.id,
    errorMessage,
    failedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  await jobSnap.ref.update({
    status: 'failed',
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

async function processPendingNotificationJobs(limit = 25) {
  const queued = await notificationJobsCollection()
    .where('status', '==', 'queued')
    .orderBy('createdAt', 'asc')
    .limit(limit)
    .get();

  const results = [];

  for (const doc of queued.docs) {
    try {
      const result = await dispatchSingleJob(doc);
      results.push(result);
    } catch (error) {
      const retries = (doc.data().retries || 0) + 1;
      const maxRetries = doc.data().maxRetries || DEFAULT_MAX_RETRIES;

      if (retries >= maxRetries) {
        await moveToErrorQueue(doc, error.message);
        results.push({ jobId: doc.id, sent: false, movedToErrorQueue: true });
      } else {
        await doc.ref.update({
          retries,
          lastError: error.message,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        results.push({ jobId: doc.id, sent: false, retryScheduled: true, retries });
      }
    }
  }

  return {
    processed: queued.size,
    results,
  };
}

module.exports = {
  enqueueNotification,
  processPendingNotificationJobs,
};
