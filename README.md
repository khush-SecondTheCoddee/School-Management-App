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
