# School Management System (Android)

A modular Android scaffold for a school ecosystem with explicit environment variants and quality gates.

## Modules

- `app`: UI shell and top-level navigation.
- `core`: shared Result wrappers and logging utilities.
- `data`: repository layer and future Firebase/remote/local data sources.
- `feature-auth`
- `feature-dashboard`
- `feature-attendance`
- `feature-homework`
- `feature-results`
- `feature-notifications`

## Build variants

`app` now defines one flavor dimension (`environment`) with:

- `dev`
- `staging`
- `prod`

Examples:

- `./gradlew :app:assembleDevDebug`
- `./gradlew :app:assembleStagingDebug`
- `./gradlew :app:assembleProdRelease`

## Firebase `google-services.json` strategy

- Place per-environment files in:
  - `app/src/dev/google-services.json`
  - `app/src/staging/google-services.json`
  - `app/src/prod/google-services.json`
- **Never commit `prod` secrets**. `.gitignore` blocks `app/src/prod/google-services.json`.
- In CI, inject production JSON from a secret and write it during the workflow only when needed.

Example CI step:

```bash
echo "$PROD_GOOGLE_SERVICES_JSON" | base64 -d > app/src/prod/google-services.json
```

## Dependency management

All versions are centralized in `gradle/libs.versions.toml` (Version Catalog).

## Static analysis / quality

- `ktlint` for formatting/style checks
- `detekt` for Kotlin static analysis
- Android lint for Android modules

Run everything locally:

```bash
./gradlew ktlintCheck detekt lintDebug test
```

## Backend RBAC and admin operations

This repository now includes a backend reference implementation under `backend/`:

- `backend/admin/bootstrap-super-admin.js`: one-time secure bootstrap for first `SUPER_ADMIN`.
- `backend/rbac/roles.js`: canonical roles (`SUPER_ADMIN`, `MANAGEMENT`, `TEACHER`, `STUDENT`, `PARENT`, `STAFF`).
- `backend/rbac/permissions.js`: feature/action permissions matrix (`read`, `write`, `approve`, `export`).
- `backend/middleware/requirePermission.js`: endpoint guard that verifies ID token and reads role from Firestore, never from client payload.
- `backend/api/adminRoutes.js`: admin endpoints for role assignment and activation/deactivation, with audit log writes.
- `firestore.rules`: server-enforced security rules aligned to RBAC.

Bootstrap example:

```bash
GOOGLE_APPLICATION_CREDENTIALS=./serviceAccountKey.json \
node backend/admin/bootstrap-super-admin.js --email=admin@school.edu --name="Founding Admin"
```

## Firestore multi-school schema (v2)

The project now uses school-scoped collections under `schools/{schoolId}`:

- `students`, `teachers`, `staff`, `classes`, `attendance`, `homework`, `results`, `announcements`
- denormalized dashboard cards at `schools/{schoolId}/read_models/dashboard/dashboard_cards/{cardId}`
- global user profiles at `users/{uid}` with a `schoolMemberships` map

See `docs/firestore-schema-v2.md` for schema details and migration/validation workflow.

### Schema migration and validation

```bash
node scripts/firestore/migrate_to_school_scoped.js --defaultSchoolId=school_demo --dryRun=true
node scripts/firestore/validate_schema_v2.js --limit=500
```

### Firestore indexes

Composite and field index definitions are maintained in `firestore.indexes.json`.
