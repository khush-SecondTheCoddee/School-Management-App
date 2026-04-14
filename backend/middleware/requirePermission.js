const admin = require('firebase-admin');
const { hasPermission } = require('../rbac/permissions');

/**
 * Authenticates a Firebase ID token and enforces RBAC using server-side role data.
 * Never trusts any role from request payload or custom headers.
 */
function requirePermission(feature, action) {
  return async function permissionGuard(req, res, next) {
    try {
      const authHeader = req.headers.authorization || '';
      if (!authHeader.startsWith('Bearer ')) {
        return res.status(401).json({ error: 'Missing Bearer token' });
      }

      const idToken = authHeader.replace('Bearer ', '').trim();
      const decoded = await admin.auth().verifyIdToken(idToken, true);

      const userDoc = await admin.firestore().collection('users').doc(decoded.uid).get();
      if (!userDoc.exists) {
        return res.status(403).json({ error: 'Access denied: account record missing' });
      }

      const userData = userDoc.data();
      const role = userData?.role;
      const isActive = userData?.isActive === true;

      if (!isActive) {
        return res.status(403).json({ error: 'Account is inactive' });
      }

      if (!hasPermission(role, feature, action)) {
        return res.status(403).json({ error: 'Access denied: insufficient permission' });
      }

      req.authContext = {
        uid: decoded.uid,
        email: decoded.email || null,
        role,
      };

      return next();
    } catch (error) {
      return res.status(401).json({ error: 'Unauthorized', details: error.message });
    }
  };
}

module.exports = { requirePermission };
