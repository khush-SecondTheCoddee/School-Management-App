const express = require('express');
const admin = require('firebase-admin');

const { requirePermission } = require('../middleware/requirePermission');
const { Actions, Features } = require('../rbac/permissions');
const { Roles } = require('../rbac/roles');
const { writeAuditLog } = require('../services/auditLogService');

const router = express.Router();

const allowedAssignableRoles = new Set(Object.values(Roles));

router.patch(
  '/admin/users/:uid/role',
  requirePermission(Features.USERS, Actions.APPROVE),
  async (req, res) => {
    const targetUid = req.params.uid;
    const nextRole = req.body?.role;

    if (!allowedAssignableRoles.has(nextRole)) {
      return res.status(400).json({ error: 'Invalid role' });
    }

    const userRef = admin.firestore().collection('users').doc(targetUid);
    const userSnap = await userRef.get();
    if (!userSnap.exists) {
      return res.status(404).json({ error: 'User not found' });
    }

    await userRef.update({
      role: nextRole,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: req.authContext.uid,
    });

    await writeAuditLog({
      actorUid: req.authContext.uid,
      actorRole: req.authContext.role,
      action: 'ROLE_ASSIGNED',
      targetUid,
      metadata: { nextRole },
    });

    return res.json({ success: true, targetUid, role: nextRole });
  }
);

router.patch(
  '/admin/users/:uid/activation',
  requirePermission(Features.USERS, Actions.APPROVE),
  async (req, res) => {
    const targetUid = req.params.uid;
    const isActive = req.body?.isActive;

    if (typeof isActive !== 'boolean') {
      return res.status(400).json({ error: 'isActive must be a boolean' });
    }

    const userRef = admin.firestore().collection('users').doc(targetUid);
    const userSnap = await userRef.get();
    if (!userSnap.exists) {
      return res.status(404).json({ error: 'User not found' });
    }

    await userRef.update({
      isActive,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedBy: req.authContext.uid,
    });

    await writeAuditLog({
      actorUid: req.authContext.uid,
      actorRole: req.authContext.role,
      action: isActive ? 'ACCOUNT_ACTIVATED' : 'ACCOUNT_DEACTIVATED',
      targetUid,
      metadata: { isActive },
    });

    return res.json({ success: true, targetUid, isActive });
  }
);

module.exports = { adminRoutes: router };
