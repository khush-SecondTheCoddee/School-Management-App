const fs = require('fs');
const path = require('path');
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const { doc, getDoc, setDoc, updateDoc } = require('firebase/firestore');

const SCHOOL_A = 'schoolA';
const SCHOOL_B = 'schoolB';

let testEnv;

function membership(role) {
  return {
    role,
    isActive: true,
    joinedAt: new Date(),
  };
}

async function seedUser(uid, role, schoolMemberships) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `users/${uid}`), {
      role,
      isActive: true,
      schoolMemberships,
      schemaVersion: 2,
    });
  });
}

test.before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-school-management',
    firestore: {
      rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });

  await seedUser('managerA', 'MANAGEMENT', { [SCHOOL_A]: membership('MANAGEMENT') });
  await seedUser('teacherA', 'TEACHER', { [SCHOOL_A]: membership('TEACHER') });
  await seedUser('staffA', 'STAFF', { [SCHOOL_A]: membership('STAFF') });
  await seedUser('studentA', 'STUDENT', { [SCHOOL_A]: membership('STUDENT') });
  await seedUser('teacherB', 'TEACHER', { [SCHOOL_B]: membership('TEACHER') });

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `schools/${SCHOOL_A}/students/std-1`), {
      studentId: 'std-1',
      name: 'Existing Student',
    });
    await setDoc(doc(db, `schools/${SCHOOL_A}/results/res-locked`), {
      resultId: 'res-locked',
      marks: 80,
      maxMarks: 100,
      status: 'PUBLISHED',
    });
  });
});

test.after(async () => {
  await testEnv.cleanup();
});

test.afterEach(async () => {
  await testEnv.clearFirestore();
  await seedUser('managerA', 'MANAGEMENT', { [SCHOOL_A]: membership('MANAGEMENT') });
  await seedUser('teacherA', 'TEACHER', { [SCHOOL_A]: membership('TEACHER') });
  await seedUser('staffA', 'STAFF', { [SCHOOL_A]: membership('STAFF') });
  await seedUser('studentA', 'STUDENT', { [SCHOOL_A]: membership('STUDENT') });
  await seedUser('teacherB', 'TEACHER', { [SCHOOL_B]: membership('TEACHER') });
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `schools/${SCHOOL_A}/students/std-1`), {
      studentId: 'std-1',
      name: 'Existing Student',
    });
    await setDoc(doc(db, `schools/${SCHOOL_A}/results/res-locked`), {
      resultId: 'res-locked',
      marks: 80,
      maxMarks: 100,
      status: 'PUBLISHED',
    });
  });
});

test('denies unauthenticated school reads', async () => {
  const anonDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anonDb, `schools/${SCHOOL_A}`)));
});

test('allows school member to read member path, blocks cross-school read', async () => {
  const teacherDb = testEnv.authenticatedContext('teacherA').firestore();
  await assertSucceeds(getDoc(doc(teacherDb, `schools/${SCHOOL_A}/students/std-1`)));

  const otherSchoolDb = testEnv.authenticatedContext('teacherB').firestore();
  await assertFails(getDoc(doc(otherSchoolDb, `schools/${SCHOOL_A}/students/std-1`)));
});

test('allows teacher to create valid result and blocks invalid marks', async () => {
  const teacherDb = testEnv.authenticatedContext('teacherA').firestore();
  await assertSucceeds(
    setDoc(doc(teacherDb, `schools/${SCHOOL_A}/results/res-1`), {
      resultId: 'res-1',
      marks: 77,
      maxMarks: 100,
      status: 'DRAFT',
    }),
  );

  await assertFails(
    setDoc(doc(teacherDb, `schools/${SCHOOL_A}/results/res-2`), {
      resultId: 'res-2',
      marks: 105,
      maxMarks: 100,
      status: 'DRAFT',
    }),
  );
});

test('enforces immutable identifiers on updates', async () => {
  const managerDb = testEnv.authenticatedContext('managerA').firestore();
  await assertFails(
    updateDoc(doc(managerDb, `schools/${SCHOOL_A}/students/std-1`), {
      studentId: 'std-2',
    }),
  );
});

test('enforces result status transitions', async () => {
  const teacherDb = testEnv.authenticatedContext('teacherA').firestore();
  await assertSucceeds(
    updateDoc(doc(teacherDb, `schools/${SCHOOL_A}/results/res-locked`), {
      status: 'FINALIZED',
    }),
  );

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `schools/${SCHOOL_A}/results/res-transition`), {
      resultId: 'res-transition',
      marks: 90,
      maxMarks: 100,
      status: 'FINALIZED',
    });
  });

  await assertFails(
    updateDoc(doc(teacherDb, `schools/${SCHOOL_A}/results/res-transition`), {
      status: 'DRAFT',
    }),
  );
});

test('enforces attendance range constraints', async () => {
  const staffDb = testEnv.authenticatedContext('staffA').firestore();

  await assertSucceeds(
    setDoc(doc(staffDb, `schools/${SCHOOL_A}/attendance/att-1`), {
      attendanceId: 'att-1',
      attendancePercentage: 95,
      sessionsPresent: 19,
      sessionsTotal: 20,
      status: 'PRESENT',
    }),
  );

  await assertFails(
    setDoc(doc(staffDb, `schools/${SCHOOL_A}/attendance/att-2`), {
      attendanceId: 'att-2',
      attendancePercentage: 130,
      sessionsPresent: 21,
      sessionsTotal: 20,
      status: 'PRESENT',
    }),
  );
});

test('blocks direct client writes to sensitive collections', async () => {
  const managerDb = testEnv.authenticatedContext('managerA').firestore();

  await assertFails(setDoc(doc(managerDb, `schools/${SCHOOL_A}/results_finalization/f1`), { status: 'FINALIZED' }));
  await assertFails(setDoc(doc(managerDb, `schools/${SCHOOL_A}/payroll/p1`), { month: '2026-03' }));
  await assertFails(setDoc(doc(managerDb, `schools/${SCHOOL_A}/critical_attendance_overrides/o1`), { reason: 'manual-fix' }));
});

test('sanity check: expected read access on sensitive collections', async () => {
  const managerDb = testEnv.authenticatedContext('managerA').firestore();
  const studentDb = testEnv.authenticatedContext('studentA').firestore();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `schools/${SCHOOL_A}/results_finalization/f1`), { status: 'FINALIZED' });
    await setDoc(doc(db, `schools/${SCHOOL_A}/payroll/p1`), { month: '2026-03' });
  });

  await assertSucceeds(getDoc(doc(managerDb, `schools/${SCHOOL_A}/results_finalization/f1`)));
  await assertSucceeds(getDoc(doc(managerDb, `schools/${SCHOOL_A}/payroll/p1`)));
  await assertFails(getDoc(doc(studentDb, `schools/${SCHOOL_A}/payroll/p1`)));
  assert.ok(true);
});
