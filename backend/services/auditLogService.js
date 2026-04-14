const admin = require('firebase-admin');

async function writeAuditLog({ actorUid, actorRole, action, targetUid, metadata = {} }) {
  return admin.firestore().collection('audit_logs').add({
    actorUid,
    actorRole,
    action,
    targetUid,
    metadata,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

module.exports = { writeAuditLog };
