const express = require('express');

const { requirePermission } = require('../middleware/requirePermission');
const { Actions, Features } = require('../rbac/permissions');
const { ingestBiometricEvents } = require('../services/biometricIngestionService');
const { reconcileAndBuildDailyAttendance } = require('../services/attendanceReconciliationService');

const router = express.Router();

function verifyWebhookSecret(req, res, next) {
  const expectedSecret = process.env.BIOMETRIC_WEBHOOK_SECRET;

  if (!expectedSecret) {
    return res.status(500).json({ error: 'BIOMETRIC_WEBHOOK_SECRET is not configured' });
  }

  const provided = req.headers['x-biometric-secret'];
  if (provided !== expectedSecret) {
    return res.status(401).json({ error: 'Invalid webhook secret' });
  }

  return next();
}

router.post('/attendance/biometric/webhook', verifyWebhookSecret, async (req, res) => {
  const source = req.body?.source || 'webhook';
  const payloads = Array.isArray(req.body?.events) ? req.body.events : [req.body];

  const ingestionResult = await ingestBiometricEvents(payloads, source);
  const reconciliationResult = await reconcileAndBuildDailyAttendance(ingestionResult.normalized);

  return res.json({
    success: true,
    ingestion: {
      processed: ingestionResult.processed,
      accepted: ingestionResult.accepted,
      rejected: ingestionResult.rejected,
    },
    reconciliation: reconciliationResult,
  });
});

router.post(
  '/attendance/biometric/batch',
  requirePermission(Features.ATTENDANCE, Actions.WRITE),
  async (req, res) => {
    const payloads = Array.isArray(req.body?.events) ? req.body.events : [];

    if (!payloads.length) {
      return res.status(400).json({ error: 'events must be a non-empty array' });
    }

    const source = req.body?.source || 'batch_upload';
    const ingestionResult = await ingestBiometricEvents(payloads, source);
    const reconciliationResult = await reconcileAndBuildDailyAttendance(ingestionResult.normalized);

    return res.json({
      success: true,
      ingestion: {
        processed: ingestionResult.processed,
        accepted: ingestionResult.accepted,
        rejected: ingestionResult.rejected,
      },
      reconciliation: reconciliationResult,
    });
  }
);

module.exports = { attendanceRoutes: router };
