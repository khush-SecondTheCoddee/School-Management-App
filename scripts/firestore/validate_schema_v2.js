#!/usr/bin/env node

/**
 * Validates schema-v2 invariants in Firestore.
 *
 * Usage:
 *   node scripts/firestore/validate_schema_v2.js --limit=500
 */

const admin = require('firebase-admin');

function parseArgs(argv) {
  return argv.reduce((acc, item) => {
    if (!item.startsWith('--')) return acc;
    const [key, value] = item.slice(2).split('=');
    acc[key] = value ?? true;
    return acc;
  }, {});
}

function assertCondition(issues, condition, message) {
  if (!condition) {
    issues.push(message);
  }
}

async function validateUsers({ firestore, limit }) {
  const issues = [];
  const usersSnap = await firestore.collection('users').limit(limit).get();

  for (const userDoc of usersSnap.docs) {
    const data = userDoc.data();
    assertCondition(issues, data.schemaVersion === 2, `users/${userDoc.id} missing schemaVersion=2`);
    assertCondition(
      issues,
      typeof data.schoolMemberships === 'object' && data.schoolMemberships != null,
      `users/${userDoc.id} missing schoolMemberships map`
    );
  }

  return issues;
}

async function validateSchoolCollection({ firestore, schoolId, collectionName, limit }) {
  const issues = [];
  const snap = await firestore
    .collection('schools')
    .doc(schoolId)
    .collection(collectionName)
    .limit(limit)
    .get();

  for (const doc of snap.docs) {
    const data = doc.data();
    assertCondition(issues, data.schemaVersion === 2, `schools/${schoolId}/${collectionName}/${doc.id} missing schemaVersion=2`);
    assertCondition(issues, data.schoolId === schoolId, `schools/${schoolId}/${collectionName}/${doc.id} schoolId mismatch`);
  }

  return issues;
}

async function validateDashboardReadModels({ firestore, schoolId, limit }) {
  const issues = [];
  const snap = await firestore
    .collection('schools')
    .doc(schoolId)
    .collection('read_models')
    .doc('dashboard')
    .collection('dashboard_cards')
    .limit(limit)
    .get();

  for (const doc of snap.docs) {
    const data = doc.data();
    assertCondition(issues, !!data.cardType, `schools/${schoolId}/read_models/dashboard/dashboard_cards/${doc.id} missing cardType`);
    assertCondition(issues, data.schemaVersion === 2, `schools/${schoolId}/read_models/dashboard/dashboard_cards/${doc.id} missing schemaVersion=2`);
  }

  return issues;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const limit = Number(args.limit || 500);

  if (!admin.apps.length) {
    admin.initializeApp();
  }

  const firestore = admin.firestore();
  const issues = [];

  issues.push(...(await validateUsers({ firestore, limit })));

  const schoolsSnap = await firestore.collection('schools').limit(limit).get();
  for (const schoolDoc of schoolsSnap.docs) {
    const schoolId = schoolDoc.id;
    for (const collectionName of [
      'students',
      'teachers',
      'staff',
      'classes',
      'attendance',
      'homework',
      'results',
      'announcements',
    ]) {
      issues.push(...(await validateSchoolCollection({ firestore, schoolId, collectionName, limit })));
    }
    issues.push(...(await validateDashboardReadModels({ firestore, schoolId, limit })));
  }

  if (issues.length > 0) {
    console.error('Schema validation failed:');
    for (const issue of issues) {
      console.error(` - ${issue}`);
    }
    process.exit(1);
  }

  console.log('Schema validation passed');
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
