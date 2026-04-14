#!/usr/bin/env node

/**
 * Secure one-time bootstrap script for creating the first SUPER_ADMIN account.
 *
 * Usage:
 *   GOOGLE_APPLICATION_CREDENTIALS=./serviceAccountKey.json node backend/admin/bootstrap-super-admin.js --email=admin@school.edu --name="Founding Admin"
 */

const crypto = require('crypto');
const admin = require('firebase-admin');
const { Roles } = require('../rbac/roles');

function parseArgs(argv) {
  return argv.reduce((acc, item) => {
    if (!item.startsWith('--')) return acc;
    const [key, value] = item.slice(2).split('=');
    acc[key] = value ?? true;
    return acc;
  }, {});
}

function generateStrongPassword(length = 24) {
  return crypto
    .randomBytes(length)
    .toString('base64')
    .replace(/[^a-zA-Z0-9]/g, 'A')
    .slice(0, length);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const email = args.email;
  const displayName = args.name || 'Super Admin';

  if (!email) {
    throw new Error('Missing required --email argument');
  }

  admin.initializeApp();

  const firestore = admin.firestore();

  const existingSuperAdmins = await firestore
    .collection('users')
    .where('role', '==', Roles.SUPER_ADMIN)
    .where('isActive', '==', true)
    .limit(1)
    .get();

  if (!existingSuperAdmins.empty) {
    throw new Error('Bootstrap aborted: an active SUPER_ADMIN already exists');
  }

  let userRecord;
  try {
    userRecord = await admin.auth().getUserByEmail(email);
  } catch (error) {
    if (error.code === 'auth/user-not-found') {
      const temporaryPassword = generateStrongPassword();
      userRecord = await admin.auth().createUser({
        email,
        displayName,
        password: temporaryPassword,
        emailVerified: true,
        disabled: false,
      });

      console.log('Created user with temporary password. Force reset on first login.');
      console.log(`temporaryPassword=${temporaryPassword}`);
    } else {
      throw error;
    }
  }

  await firestore.collection('users').doc(userRecord.uid).set(
    {
      email,
      displayName,
      role: Roles.SUPER_ADMIN,
      isActive: true,
      mustRotatePassword: true,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      bootstrappedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  await firestore.collection('audit_logs').add({
    action: 'BOOTSTRAP_SUPER_ADMIN',
    actorUid: 'system-bootstrap-script',
    actorRole: Roles.SUPER_ADMIN,
    targetUid: userRecord.uid,
    metadata: { email },
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  console.log(`SUPER_ADMIN bootstrapped successfully: uid=${userRecord.uid}, email=${email}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
