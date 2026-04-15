const express = require('express');

const { requirePermission } = require('../middleware/requirePermission');
const { Actions, Features } = require('../rbac/permissions');
const { enqueueNotification, processPendingNotificationJobs } = require('../services/notificationDispatcherService');
const { NotificationTemplates } = require('../services/notificationTemplates');
const { NotificationTypes } = require('../services/notificationTypes');

const router = express.Router();

router.get(
  '/notifications/templates',
  requirePermission(Features.NOTIFICATIONS, Actions.READ),
  async (_req, res) => {
    return res.json({
      success: true,
      templates: NotificationTemplates,
      supportedTypes: Object.values(NotificationTypes),
    });
  }
);

router.post(
  '/notifications/dispatch',
  requirePermission(Features.NOTIFICATIONS, Actions.WRITE),
  async (req, res) => {
    const type = req.body?.type;
    if (!Object.values(NotificationTypes).includes(type)) {
      return res.status(400).json({ error: 'Unsupported notification type' });
    }

    const result = await enqueueNotification({
      type,
      locale: req.body?.locale || 'en',
      templateContext: req.body?.templateContext || {},
      target: req.body?.target || {},
      data: req.body?.data || {},
      route: req.body?.route || 'notifications/inbox',
      overrides: req.body?.overrides || {},
      maxRetries: req.body?.maxRetries,
      createdBy: req.authContext.uid,
    });

    return res.status(202).json({ success: true, ...result });
  }
);

router.post(
  '/notifications/process-queue',
  requirePermission(Features.NOTIFICATIONS, Actions.APPROVE),
  async (req, res) => {
    const limit = Number(req.body?.limit || 25);
    const outcome = await processPendingNotificationJobs(limit);

    return res.json({ success: true, ...outcome });
  }
);

module.exports = { notificationRoutes: router };
