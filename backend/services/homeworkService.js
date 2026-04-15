const admin = require('firebase-admin');

const HOMEWORK_STATUS = Object.freeze({
  ACTIVE: 'ACTIVE',
  ARCHIVED: 'ARCHIVED',
});

const SUBMISSION_STATUS = Object.freeze({
  SUBMITTED: 'SUBMITTED',
  LATE: 'LATE',
  MISSING: 'MISSING',
  GRADED: 'GRADED',
});

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function normalizeAttachmentList(value) {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .filter((entry) => entry && typeof entry === 'object')
    .map((entry) => ({
      fileName: isNonEmptyString(entry.fileName) ? entry.fileName.trim() : null,
      fileUrl: isNonEmptyString(entry.fileUrl) ? entry.fileUrl.trim() : null,
      mimeType: isNonEmptyString(entry.mimeType) ? entry.mimeType.trim() : null,
      sizeBytes: Number.isInteger(entry.sizeBytes) && entry.sizeBytes >= 0 ? entry.sizeBytes : null,
    }))
    .filter((entry) => entry.fileName && entry.fileUrl);
}

function normalizePlagiarismMetadata(value) {
  if (!value || typeof value !== 'object') {
    return {
      enabled: false,
      externalSubmissionId: null,
      provider: null,
      checksumSha256: null,
      processedAt: null,
    };
  }

  return {
    enabled: value.enabled === true,
    externalSubmissionId: isNonEmptyString(value.externalSubmissionId) ? value.externalSubmissionId.trim() : null,
    provider: isNonEmptyString(value.provider) ? value.provider.trim() : null,
    checksumSha256: isNonEmptyString(value.checksumSha256) ? value.checksumSha256.trim() : null,
    processedAt: value.processedAt || null,
  };
}

function parseDueAt(rawDueAt) {
  if (!rawDueAt) {
    return null;
  }

  const parsedDate = new Date(rawDueAt);
  if (Number.isNaN(parsedDate.getTime())) {
    return null;
  }

  return admin.firestore.Timestamp.fromDate(parsedDate);
}

function buildHomeworkPayload(payload, actorUid, existing = null) {
  const dueAt = parseDueAt(payload.dueAt ?? existing?.dueAt?.toDate?.()?.toISOString());
  if (!dueAt) {
    throw new Error('Invalid dueAt. Expected ISO date string.');
  }

  if (!isNonEmptyString(payload.title ?? existing?.title)) {
    throw new Error('title is required');
  }

  const createdAt = existing?.createdAt || admin.firestore.FieldValue.serverTimestamp();
  const createdBy = existing?.createdBy || actorUid;

  return {
    title: (payload.title ?? existing?.title).trim(),
    description: isNonEmptyString(payload.description)
      ? payload.description.trim()
      : (existing?.description || ''),
    classId: isNonEmptyString(payload.classId) ? payload.classId.trim() : (existing?.classId || null),
    subjectId: isNonEmptyString(payload.subjectId) ? payload.subjectId.trim() : (existing?.subjectId || null),
    dueAt,
    attachmentRefs: normalizeAttachmentList(payload.attachmentRefs ?? existing?.attachmentRefs),
    plagiarismMetadataHook: normalizePlagiarismMetadata(payload.plagiarismMetadataHook ?? existing?.plagiarismMetadataHook),
    status: payload.status || existing?.status || HOMEWORK_STATUS.ACTIVE,
    createdAt,
    createdBy,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedBy: actorUid,
  };
}

function buildSubmissionPayload(payload, actorUid, homeworkDueAt, existing = null) {
  const now = admin.firestore.Timestamp.now();
  const submittedAt = now;
  const isLate = homeworkDueAt && submittedAt.toMillis() > homeworkDueAt.toMillis();

  return {
    studentUid: actorUid,
    submissionText: isNonEmptyString(payload.submissionText)
      ? payload.submissionText.trim()
      : (existing?.submissionText || ''),
    attachmentRefs: normalizeAttachmentList(payload.attachmentRefs ?? existing?.attachmentRefs),
    plagiarismMetadataHook: normalizePlagiarismMetadata(payload.plagiarismMetadataHook ?? existing?.plagiarismMetadataHook),
    status: payload.status || (isLate ? SUBMISSION_STATUS.LATE : SUBMISSION_STATUS.SUBMITTED),
    submittedAt,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
}

async function queueOverdueReminders({ schoolId, now = admin.firestore.Timestamp.now() }) {
  const db = admin.firestore();
  const homeworkSnap = await db
    .collection(`schools/${schoolId}/homework`)
    .where('dueAt', '<=', now)
    .where('status', '==', HOMEWORK_STATUS.ACTIVE)
    .get();

  const batch = db.batch();
  let queued = 0;

  for (const homeworkDoc of homeworkSnap.docs) {
    const homework = homeworkDoc.data();
    const submissionsSnap = await homeworkDoc.ref.collection('submissions').get();
    const submittedBy = new Set(submissionsSnap.docs.map((doc) => doc.data().studentUid).filter(Boolean));
    const assignees = Array.isArray(homework.assignedStudentUids) ? homework.assignedStudentUids : [];

    for (const studentUid of assignees) {
      if (submittedBy.has(studentUid)) {
        continue;
      }

      const reminderRef = db.collection(`schools/${schoolId}/notification_queue`).doc();
      batch.set(reminderRef, {
        type: 'HOMEWORK_OVERDUE_REMINDER',
        schoolId,
        studentUid,
        homeworkId: homeworkDoc.id,
        dueAt: homework.dueAt,
        enqueuedAt: admin.firestore.FieldValue.serverTimestamp(),
        metadata: {
          classId: homework.classId || null,
          title: homework.title || null,
        },
      });
      queued += 1;
    }
  }

  if (queued > 0) {
    await batch.commit();
  }

  return { queued, scannedHomework: homeworkSnap.size };
}

module.exports = {
  HOMEWORK_STATUS,
  SUBMISSION_STATUS,
  buildHomeworkPayload,
  buildSubmissionPayload,
  queueOverdueReminders,
};
