const express = require('express');
const admin = require('firebase-admin');

const { requirePermission } = require('../middleware/requirePermission');
const { Actions, Features } = require('../rbac/permissions');
const {
  buildHomeworkPayload,
  buildSubmissionPayload,
  queueOverdueReminders,
} = require('../services/homeworkService');

const router = express.Router();

async function resolveAuthContext(req) {
  const authHeader = req.headers.authorization || '';
  if (!authHeader.startsWith('Bearer ')) {
    return null;
  }

  const idToken = authHeader.replace('Bearer ', '').trim();
  const decoded = await admin.auth().verifyIdToken(idToken, true);
  const userDoc = await admin.firestore().collection('users').doc(decoded.uid).get();

  if (!userDoc.exists || userDoc.data()?.isActive !== true) {
    return null;
  }

  return {
    uid: decoded.uid,
    role: userDoc.data()?.role,
    email: decoded.email || null,
    userData: userDoc.data(),
  };
}

router.post(
  '/schools/:schoolId/homework',
  requirePermission(Features.HOMEWORK, Actions.WRITE),
  async (req, res) => {
    try {
      const { schoolId } = req.params;
      const payload = buildHomeworkPayload(req.body || {}, req.authContext.uid);
      const homeworkRef = admin.firestore().collection(`schools/${schoolId}/homework`).doc();
      await homeworkRef.set({
        homeworkId: homeworkRef.id,
        ...payload,
      });

      return res.status(201).json({ success: true, homeworkId: homeworkRef.id });
    } catch (error) {
      return res.status(400).json({ error: error.message });
    }
  },
);

router.patch(
  '/schools/:schoolId/homework/:homeworkId',
  requirePermission(Features.HOMEWORK, Actions.WRITE),
  async (req, res) => {
    try {
      const { schoolId, homeworkId } = req.params;
      const homeworkRef = admin.firestore().doc(`schools/${schoolId}/homework/${homeworkId}`);
      const snap = await homeworkRef.get();
      if (!snap.exists) {
        return res.status(404).json({ error: 'Homework not found' });
      }

      const payload = buildHomeworkPayload(req.body || {}, req.authContext.uid, snap.data());
      await homeworkRef.update(payload);
      return res.json({ success: true, homeworkId });
    } catch (error) {
      return res.status(400).json({ error: error.message });
    }
  },
);

router.get('/schools/:schoolId/homework', async (req, res) => {
  try {
    const authContext = await resolveAuthContext(req);
    if (!authContext) {
      return res.status(401).json({ error: 'Unauthorized' });
    }

    const { schoolId } = req.params;
    const { classId, studentUid } = req.query;
    const db = admin.firestore();

    let query = db.collection(`schools/${schoolId}/homework`);
    if (classId) {
      query = query.where('classId', '==', classId);
    }

    const snap = await query.get();
    let records = snap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));

    if (authContext.role === 'STUDENT') {
      records = records.filter((item) => {
        const assignees = Array.isArray(item.assignedStudentUids) ? item.assignedStudentUids : [];
        return assignees.length === 0 || assignees.includes(authContext.uid);
      });
    }

    if (authContext.role === 'PARENT') {
      const childIds = authContext.userData?.childStudentUidsBySchool?.[schoolId] || [];
      records = records.filter((item) => {
        const assignees = Array.isArray(item.assignedStudentUids) ? item.assignedStudentUids : [];
        return assignees.some((assignee) => childIds.includes(assignee));
      });
    }

    if (studentUid) {
      records = records.filter((item) => {
        const assignees = Array.isArray(item.assignedStudentUids) ? item.assignedStudentUids : [];
        return assignees.includes(studentUid);
      });
    }

    return res.json({ success: true, records });
  } catch (error) {
    return res.status(400).json({ error: error.message });
  }
});

router.post('/schools/:schoolId/homework/:homeworkId/submissions', async (req, res) => {
  try {
    const authContext = await resolveAuthContext(req);
    if (!authContext) {
      return res.status(401).json({ error: 'Unauthorized' });
    }

    if (authContext.role !== 'STUDENT') {
      return res.status(403).json({ error: 'Only student accounts can submit homework' });
    }

    const { schoolId, homeworkId } = req.params;
    const homeworkRef = admin.firestore().doc(`schools/${schoolId}/homework/${homeworkId}`);
    const homeworkSnap = await homeworkRef.get();
    if (!homeworkSnap.exists) {
      return res.status(404).json({ error: 'Homework not found' });
    }

    const submissionRef = homeworkRef.collection('submissions').doc(authContext.uid);
    const existing = await submissionRef.get();
    const payload = buildSubmissionPayload(
      req.body || {},
      authContext.uid,
      homeworkSnap.data()?.dueAt,
      existing.exists ? existing.data() : null,
    );

    await submissionRef.set({
      submissionId: authContext.uid,
      homeworkId,
      schoolId,
      ...payload,
    }, { merge: true });

    return res.status(201).json({ success: true, submissionId: authContext.uid, status: payload.status });
  } catch (error) {
    return res.status(400).json({ error: error.message });
  }
});

router.get('/schools/:schoolId/homework/:homeworkId/submissions/:studentUid', async (req, res) => {
  try {
    const authContext = await resolveAuthContext(req);
    if (!authContext) {
      return res.status(401).json({ error: 'Unauthorized' });
    }

    const { schoolId, homeworkId, studentUid } = req.params;
    const isSelf = authContext.uid === studentUid;
    const isTeacherLike = ['TEACHER', 'MANAGEMENT', 'SUPER_ADMIN', 'STAFF'].includes(authContext.role);
    const childIds = authContext.userData?.childStudentUidsBySchool?.[schoolId] || [];
    const isParentOfStudent = authContext.role === 'PARENT' && childIds.includes(studentUid);

    if (!(isSelf || isTeacherLike || isParentOfStudent)) {
      return res.status(403).json({ error: 'Access denied' });
    }

    const submissionSnap = await admin
      .firestore()
      .doc(`schools/${schoolId}/homework/${homeworkId}/submissions/${studentUid}`)
      .get();

    if (!submissionSnap.exists) {
      return res.status(404).json({ error: 'Submission not found' });
    }

    return res.json({ success: true, submission: { id: submissionSnap.id, ...submissionSnap.data() } });
  } catch (error) {
    return res.status(400).json({ error: error.message });
  }
});

router.post(
  '/schools/:schoolId/jobs/homework-overdue-reminders',
  requirePermission(Features.NOTIFICATIONS, Actions.WRITE),
  async (req, res) => {
    try {
      const { schoolId } = req.params;
      const result = await queueOverdueReminders({ schoolId });
      return res.json({ success: true, ...result });
    } catch (error) {
      return res.status(400).json({ error: error.message });
    }
  },
);

module.exports = { homeworkRoutes: router };
