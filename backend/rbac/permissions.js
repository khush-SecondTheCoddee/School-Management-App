const { Roles } = require('./roles');

const Actions = Object.freeze({
  READ: 'read',
  WRITE: 'write',
  APPROVE: 'approve',
  EXPORT: 'export',
});

const Features = Object.freeze({
  USERS: 'users',
  ATTENDANCE: 'attendance',
  HOMEWORK: 'homework',
  RESULTS: 'results',
  NOTIFICATIONS: 'notifications',
  FINANCE: 'finance',
  AUDIT_LOGS: 'audit_logs',
});

/**
 * Permissions matrix by role and feature.
 * Each action is explicit to avoid implicit broad grants.
 */
const PERMISSIONS = Object.freeze({
  [Roles.SUPER_ADMIN]: {
    [Features.USERS]: [Actions.READ, Actions.WRITE, Actions.APPROVE, Actions.EXPORT],
    [Features.ATTENDANCE]: [Actions.READ, Actions.WRITE, Actions.APPROVE, Actions.EXPORT],
    [Features.HOMEWORK]: [Actions.READ, Actions.WRITE, Actions.APPROVE, Actions.EXPORT],
    [Features.RESULTS]: [Actions.READ, Actions.WRITE, Actions.APPROVE, Actions.EXPORT],
    [Features.NOTIFICATIONS]: [Actions.READ, Actions.WRITE, Actions.APPROVE, Actions.EXPORT],
    [Features.FINANCE]: [Actions.READ, Actions.WRITE, Actions.APPROVE, Actions.EXPORT],
    [Features.AUDIT_LOGS]: [Actions.READ, Actions.EXPORT],
  },
  [Roles.MANAGEMENT]: {
    [Features.USERS]: [Actions.READ, Actions.WRITE, Actions.APPROVE],
    [Features.ATTENDANCE]: [Actions.READ, Actions.WRITE, Actions.APPROVE, Actions.EXPORT],
    [Features.HOMEWORK]: [Actions.READ, Actions.WRITE, Actions.APPROVE],
    [Features.RESULTS]: [Actions.READ, Actions.WRITE, Actions.APPROVE, Actions.EXPORT],
    [Features.NOTIFICATIONS]: [Actions.READ, Actions.WRITE],
    [Features.FINANCE]: [Actions.READ, Actions.APPROVE, Actions.EXPORT],
    [Features.AUDIT_LOGS]: [Actions.READ],
  },
  [Roles.TEACHER]: {
    [Features.ATTENDANCE]: [Actions.READ, Actions.WRITE],
    [Features.HOMEWORK]: [Actions.READ, Actions.WRITE],
    [Features.RESULTS]: [Actions.READ, Actions.WRITE],
    [Features.NOTIFICATIONS]: [Actions.READ, Actions.WRITE],
  },
  [Roles.STUDENT]: {
    [Features.ATTENDANCE]: [Actions.READ],
    [Features.HOMEWORK]: [Actions.READ],
    [Features.RESULTS]: [Actions.READ],
    [Features.NOTIFICATIONS]: [Actions.READ],
  },
  [Roles.PARENT]: {
    [Features.ATTENDANCE]: [Actions.READ],
    [Features.HOMEWORK]: [Actions.READ],
    [Features.RESULTS]: [Actions.READ],
    [Features.NOTIFICATIONS]: [Actions.READ],
    [Features.FINANCE]: [Actions.READ],
  },
  [Roles.STAFF]: {
    [Features.ATTENDANCE]: [Actions.READ, Actions.WRITE],
    [Features.NOTIFICATIONS]: [Actions.READ, Actions.WRITE],
    [Features.FINANCE]: [Actions.READ],
  },
});

function hasPermission(role, feature, action) {
  if (!role || !feature || !action) {
    return false;
  }

  const rolePermissions = PERMISSIONS[role] || {};
  const allowedActions = rolePermissions[feature] || [];
  return allowedActions.includes(action);
}

module.exports = {
  Actions,
  Features,
  PERMISSIONS,
  hasPermission,
};
