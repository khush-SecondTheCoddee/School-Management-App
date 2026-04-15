const express = require('express');
const admin = require('firebase-admin');

const { requirePermission } = require('../middleware/requirePermission');
const { Actions, Features } = require('../rbac/permissions');
const { writeAuditLog } = require('../services/auditLogService');
const {
  RESULT_STATUSES,
  validateExamModel,
  validateMarksPayload,
  buildResultSummary,
  buildPublishAuditHash,
  toCsv,
  toSimplePdfBuffer,
} = require('../services/resultsService');

const router = express.Router();

function collectionPath(schoolId, leaf) {
  return admin.firestore().collection('schools').doc(schoolId).collection(leaf);
}

router.post(
  '/schools/:schoolId/exams',
  requirePermission(Features.RESULTS, Actions.WRITE),
  async (req, res) => {
    const { schoolId } = req.params;
    const validationError = validateExamModel(req.body);
    if (validationError) {
      return res.status(400).json({ error: validationError });
    }

    const examRef = collectionPath(schoolId, 'exams').doc();
    await examRef.set({
      examId: examRef.id,
      schoolId,
      ...req.body,
      status: RESULT_STATUSES.DRAFT,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      createdBy: req.authContext.uid,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: req.authContext.uid,
    });

    return res.status(201).json({ success: true, examId: examRef.id });
  }
);

router.post(
  '/schools/:schoolId/exams/:examId/marks',
  requirePermission(Features.RESULTS, Actions.WRITE),
  async (req, res) => {
    const { schoolId, examId } = req.params;
    const mode = req.body?.mode === 'FINAL' ? 'FINAL' : 'DRAFT';
    const entry = req.body?.entry;

    const examRef = collectionPath(schoolId, 'exams').doc(examId);
    const examSnap = await examRef.get();
    if (!examSnap.exists) {
      return res.status(404).json({ error: 'Exam not found' });
    }

    const examModel = examSnap.data();
    const validationError = validateMarksPayload(entry, examModel);
    if (validationError) {
      return res.status(400).json({ error: validationError });
    }

    const summary = buildResultSummary(entry, examModel);
    const resultEntryRef = collectionPath(schoolId, 'results').doc();

    await resultEntryRef.set({
      resultId: resultEntryRef.id,
      schoolId,
      examId,
      examName: examModel.name,
      studentId: entry.studentId,
      status: mode === 'FINAL' ? RESULT_STATUSES.PENDING_APPROVAL : RESULT_STATUSES.DRAFT,
      summary,
      subjectMarks: entry.subjectMarks,
      teacherRemarks: entry.teacherRemarks || '',
      draftedAt: admin.firestore.FieldValue.serverTimestamp(),
      submittedAt: mode === 'FINAL' ? admin.firestore.FieldValue.serverTimestamp() : null,
      publishedAt: null,
      createdBy: req.authContext.uid,
      updatedBy: req.authContext.uid,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return res.status(201).json({ success: true, resultId: resultEntryRef.id, status: mode });
  }
);

router.patch(
  '/schools/:schoolId/results/:resultId/approval',
  requirePermission(Features.RESULTS, Actions.APPROVE),
  async (req, res) => {
    const { schoolId, resultId } = req.params;
    const decision = req.body?.decision;

    if (!['APPROVE', 'REJECT'].includes(decision)) {
      return res.status(400).json({ error: 'decision must be APPROVE or REJECT' });
    }

    const resultRef = collectionPath(schoolId, 'results').doc(resultId);
    const resultSnap = await resultRef.get();
    if (!resultSnap.exists) {
      return res.status(404).json({ error: 'Result entry not found' });
    }

    const resultData = resultSnap.data();
    if (resultData.status === RESULT_STATUSES.PUBLISHED) {
      return res.status(409).json({ error: 'Published result is immutable' });
    }

    if (resultData.status !== RESULT_STATUSES.PENDING_APPROVAL && decision === 'APPROVE') {
      return res.status(409).json({ error: 'Only pending results can be approved' });
    }

    const nextStatus = decision === 'APPROVE' ? RESULT_STATUSES.PUBLISHED : RESULT_STATUSES.REJECTED;
    await resultRef.update({
      status: nextStatus,
      publishedAt: decision === 'APPROVE' ? admin.firestore.FieldValue.serverTimestamp() : null,
      approvedBy: req.authContext.uid,
      approvalComment: req.body?.comment || '',
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: req.authContext.uid,
    });

    if (decision === 'APPROVE') {
      const auditPayload = {
        schoolId,
        resultId,
        approvedBy: req.authContext.uid,
        approvedAt: new Date().toISOString(),
        summary: resultData.summary,
      };

      const publishHash = buildPublishAuditHash(auditPayload);
      await collectionPath(schoolId, 'results_publish_audit').add({
        ...auditPayload,
        publishHash,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      await writeAuditLog({
        actorUid: req.authContext.uid,
        actorRole: req.authContext.role,
        action: 'RESULT_PUBLISHED',
        targetUid: resultData.studentId,
        metadata: { schoolId, resultId, publishHash },
      });
    }

    return res.json({ success: true, resultId, status: nextStatus });
  }
);

router.get(
  '/schools/:schoolId/students/:studentId/results',
  requirePermission(Features.RESULTS, Actions.READ),
  async (req, res) => {
    const { schoolId, studentId } = req.params;

    const snapshot = await collectionPath(schoolId, 'results')
      .where('studentId', '==', studentId)
      .where('status', '==', RESULT_STATUSES.PUBLISHED)
      .orderBy('publishedAt', 'desc')
      .limit(10)
      .get();

    const publishedResults = snapshot.docs.map((doc) => ({
      resultId: doc.id,
      ...doc.data(),
    }));

    const trend = publishedResults
      .slice()
      .reverse()
      .map((item, index) => ({
        index: index + 1,
        examName: item.examName,
        overallPercentage: item.summary?.overallPercentage || 0,
      }));

    return res.json({
      studentId,
      results: publishedResults,
      trend,
    });
  }
);

router.get(
  '/schools/:schoolId/results/:resultId/report-card',
  requirePermission(Features.RESULTS, Actions.READ),
  async (req, res) => {
    const { schoolId, resultId } = req.params;
    const resultSnap = await collectionPath(schoolId, 'results').doc(resultId).get();

    if (!resultSnap.exists) {
      return res.status(404).json({ error: 'Result not found' });
    }

    const result = resultSnap.data();
    if (result.status !== RESULT_STATUSES.PUBLISHED) {
      return res.status(409).json({ error: 'Report card is available only for published results' });
    }

    return res.json({
      resultId,
      reportCard: {
        examName: result.examName,
        studentId: result.studentId,
        summary: result.summary,
        teacherRemarks: result.teacherRemarks,
        publishedAt: result.publishedAt,
      },
      downloads: {
        csv: `/v1/schools/${schoolId}/results/${resultId}/export.csv`,
        pdf: `/v1/schools/${schoolId}/results/${resultId}/export.pdf`,
      },
    });
  }
);

router.get(
  '/schools/:schoolId/results/:resultId/export.csv',
  requirePermission(Features.RESULTS, Actions.EXPORT),
  async (req, res) => {
    const { schoolId, resultId } = req.params;
    const resultSnap = await collectionPath(schoolId, 'results').doc(resultId).get();

    if (!resultSnap.exists) {
      return res.status(404).json({ error: 'Result not found' });
    }

    const result = { resultId, ...resultSnap.data() };
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', `attachment; filename="report-${resultId}.csv"`);
    return res.send(toCsv(result));
  }
);

router.get(
  '/schools/:schoolId/results/:resultId/export.pdf',
  requirePermission(Features.RESULTS, Actions.EXPORT),
  async (req, res) => {
    const { schoolId, resultId } = req.params;
    const resultSnap = await collectionPath(schoolId, 'results').doc(resultId).get();

    if (!resultSnap.exists) {
      return res.status(404).json({ error: 'Result not found' });
    }

    const result = { resultId, ...resultSnap.data() };
    const pdfBuffer = toSimplePdfBuffer(result);

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="report-${resultId}.pdf"`);
    return res.send(pdfBuffer);
  }
);

module.exports = { resultsRoutes: router };
