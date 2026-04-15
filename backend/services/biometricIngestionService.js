const admin = require('firebase-admin');

function normalizeBiometricPayload(payload = {}, source = 'webhook') {
  const directionRaw =
    payload.direction || payload.eventType || payload.type || payload.punch || payload.status;
  const normalizedDirection = String(directionRaw || '')
    .trim()
    .toLowerCase();

  const inSynonyms = new Set(['in', 'checkin', 'check_in', 'entry', 'punch_in']);
  const outSynonyms = new Set(['out', 'checkout', 'check_out', 'exit', 'punch_out']);

  const direction = inSynonyms.has(normalizedDirection)
    ? 'in'
    : outSynonyms.has(normalizedDirection)
      ? 'out'
      : null;

  const biometricId =
    payload.biometricId || payload.employeeCode || payload.userId || payload.uid || payload.empId;
  const deviceId = payload.deviceId || payload.terminalId || payload.readerId || 'unknown-device';

  const timestampCandidate = payload.timestamp || payload.time || payload.eventTime;
  const eventDate = timestampCandidate ? new Date(timestampCandidate) : new Date();

  if (!biometricId || !direction || Number.isNaN(eventDate.getTime())) {
    return { valid: false, reason: 'biometricId, valid direction, and valid timestamp are required' };
  }

  return {
    valid: true,
    event: {
      biometricId: String(biometricId),
      direction,
      timestamp: admin.firestore.Timestamp.fromDate(eventDate),
      timestampIso: eventDate.toISOString(),
      deviceId: String(deviceId),
      source,
      rawPayload: payload,
      ingestedAt: admin.firestore.FieldValue.serverTimestamp(),
    },
  };
}

async function ingestBiometricEvents(events, source) {
  const db = admin.firestore();
  const normalized = [];
  const rejected = [];

  events.forEach((payload, index) => {
    const result = normalizeBiometricPayload(payload, source);
    if (result.valid) {
      normalized.push(result.event);
    } else {
      rejected.push({ index, reason: result.reason, payload });
    }
  });

  const writes = normalized.map((event) => db.collection('biometricEvents').add(event));
  await Promise.all(writes);

  return {
    processed: events.length,
    accepted: normalized.length,
    rejected,
    normalized,
  };
}

module.exports = {
  normalizeBiometricPayload,
  ingestBiometricEvents,
};
