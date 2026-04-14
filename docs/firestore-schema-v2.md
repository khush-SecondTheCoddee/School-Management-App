# Firestore Schema v2 (School Scoped)

## Collections

### Global user profiles
- `users/{uid}`
  - `role`: global platform role (`SUPER_ADMIN`, etc.)
  - `isActive`: boolean
  - `schoolMemberships`: map keyed by `schoolId`
    - `role`: school-specific role (`MANAGEMENT`, `TEACHER`, `STUDENT`, `STAFF`)
    - `isActive`: membership state
    - `joinedAt`: timestamp
  - `schemaVersion`: `2`

### School scoped domain collections
- `schools/{schoolId}/students/{studentId}`
- `schools/{schoolId}/teachers/{teacherId}`
- `schools/{schoolId}/staff/{staffId}`
- `schools/{schoolId}/classes/{classId}`
- `schools/{schoolId}/attendance/{attendanceId}`
- `schools/{schoolId}/homework/{homeworkId}`
- `schools/{schoolId}/results/{resultId}`
- `schools/{schoolId}/announcements/{announcementId}`

### Denormalized read models
- `schools/{schoolId}/read_models/dashboard_cards/{cardId}`
  - `cardType`: e.g. `attendance_today`, `overdue_homework`, `result_summary`
  - `payload`: precomputed aggregates for dashboard cards
  - `updatedAt`: timestamp
  - `schemaVersion`: `2`

## Indexing plan
See `firestore.indexes.json` for:
- class-level attendance timeline queries
- result summary rollups per class/term and per student
- overdue homework scans by class and by student
- dashboard card lookups by type and freshness

## Migration and validation
- migration script: `scripts/firestore/migrate_to_school_scoped.js`
- validator script: `scripts/firestore/validate_schema_v2.js`
