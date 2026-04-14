#!/usr/bin/env node

/**
 * Migrates legacy top-level collections into school-scoped collections:
 * - attendance -> schools/{schoolId}/attendance
 * - homework -> schools/{schoolId}/homework
 * - results -> schools/{schoolId}/results
 *
 * Usage:
 *   node scripts/firestore/migrate_to_school_scoped.js --defaultSchoolId=school_demo --batchSize=250 --dryRun=true
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

function asBoolean(value, fallback = false) {
  if (typeof value === 'boolean') return value;
  if (value == null) return fallback;
  return String(value).toLowerCase() === 'true';
}

async function migrateCollection({ firestore, sourceCollection, defaultSchoolId, batchSize, dryRun }) {
  const snapshot = await firestore.collection(sourceCollection).get();
  if (snapshot.empty) {
    console.log(`[skip] ${sourceCollection}: no documents found`);
    return { migrated: 0, skipped: 0 };
  }

  let migrated = 0;
  let skipped = 0;
  let batch = firestore.batch();
  let pending = 0;

  for (const doc of snapshot.docs) {
    const data = doc.data();
    const schoolId = data.schoolId || defaultSchoolId;

    if (!schoolId) {
      skipped += 1;
      console.warn(`[skip] ${sourceCollection}/${doc.id}: missing schoolId and no --defaultSchoolId`);
      continue;
    }

    const targetRef = firestore
      .collection('schools')
      .doc(String(schoolId))
      .collection(sourceCollection)
      .doc(doc.id);

    const migratedData = {
      ...data,
      schoolId,
      schemaVersion: 2,
      migratedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    if (!dryRun) {
      batch.set(targetRef, migratedData, { merge: true });
      pending += 1;
      if (pending >= batchSize) {
        await batch.commit();
        batch = firestore.batch();
        pending = 0;
      }
    }

    migrated += 1;
  }

  if (!dryRun && pending > 0) {
    await batch.commit();
  }

  console.log(`[done] ${sourceCollection}: migrated=${migrated}, skipped=${skipped}, dryRun=${dryRun}`);
  return { migrated, skipped };
}

async function migrateUsers({ firestore, dryRun }) {
  const usersSnap = await firestore.collection('users').get();
  let processed = 0;

  let batch = firestore.batch();
  let pending = 0;

  for (const userDoc of usersSnap.docs) {
    const data = userDoc.data();

    if (data.schemaVersion === 2 && data.schoolMemberships) {
      continue;
    }

    const schoolId = data.schoolId || null;
    const membershipRole = data.role || 'STAFF';
    const schoolMemberships = schoolId
      ? {
          [schoolId]: {
            role: membershipRole,
            isActive: data.isActive !== false,
            joinedAt: data.createdAt || admin.firestore.FieldValue.serverTimestamp(),
          },
        }
      : {};

    if (!dryRun) {
      batch.set(
        userDoc.ref,
        {
          schoolMemberships,
          schemaVersion: 2,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
      pending += 1;
      if (pending >= 300) {
        await batch.commit();
        batch = firestore.batch();
        pending = 0;
      }
    }

    processed += 1;
  }

  if (!dryRun && pending > 0) {
    await batch.commit();
  }

  console.log(`[done] users: processed=${processed}, dryRun=${dryRun}`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const defaultSchoolId = args.defaultSchoolId || null;
  const batchSize = Number(args.batchSize || 250);
  const dryRun = asBoolean(args.dryRun, true);

  if (!admin.apps.length) {
    admin.initializeApp();
  }

  const firestore = admin.firestore();

  const collections = ['attendance', 'homework', 'results'];

  for (const collectionName of collections) {
    await migrateCollection({
      firestore,
      sourceCollection: collectionName,
      defaultSchoolId,
      batchSize,
      dryRun,
    });
  }

  await migrateUsers({ firestore, dryRun });

  console.log('Migration finished');
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
