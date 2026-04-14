#!/usr/bin/env bash
set -euo pipefail

ROOT_BUILD="build.gradle.kts"
APP_BUILD="app/build.gradle.kts"
VERSIONS_FILE="gradle/libs.versions.toml"

require_line() {
  local file="$1"
  local pattern="$2"

  if ! rg -q "$pattern" "$file"; then
    echo "❌ Missing expected Firebase config in $file: $pattern"
    exit 1
  fi
}

require_line "$ROOT_BUILD" 'alias\(libs\.plugins\.google\.services\) apply false'
require_line "$ROOT_BUILD" 'alias\(libs\.plugins\.firebase\.crashlytics\) apply false'

require_line "$APP_BUILD" 'alias\(libs\.plugins\.google\.services\)'
require_line "$APP_BUILD" 'alias\(libs\.plugins\.firebase\.crashlytics\)'
require_line "$APP_BUILD" 'implementation\(platform\(libs\.firebase\.bom\)\)'
require_line "$APP_BUILD" 'implementation\(libs\.firebase\.auth\)'
require_line "$APP_BUILD" 'implementation\(libs\.firebase\.firestore\)'
require_line "$APP_BUILD" 'implementation\(libs\.firebase\.messaging\)'
require_line "$APP_BUILD" 'implementation\(libs\.firebase\.analytics\)'
require_line "$APP_BUILD" 'implementation\(libs\.firebase\.crashlytics\)'

require_line "$VERSIONS_FILE" '^firebaseBom\s*='
require_line "$VERSIONS_FILE" '^firebaseCrashlyticsPlugin\s*='
require_line "$VERSIONS_FILE" '^firebase-bom\s*='
require_line "$VERSIONS_FILE" '^firebase-auth\s*='
require_line "$VERSIONS_FILE" '^firebase-firestore\s*='
require_line "$VERSIONS_FILE" '^firebase-messaging\s*='
require_line "$VERSIONS_FILE" '^firebase-analytics\s*='
require_line "$VERSIONS_FILE" '^firebase-crashlytics\s*='
require_line "$VERSIONS_FILE" '^firebase-crashlytics\s*=\s*\{ id = "com\.google\.firebase\.crashlytics"'

echo "✅ Firebase dependency/plugin validation passed"
