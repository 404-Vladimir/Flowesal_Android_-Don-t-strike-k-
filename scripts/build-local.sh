#!/usr/bin/env bash
set -euo pipefail
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is required. Android Studio can provide the SDK; install Gradle 8.10+ or use GitHub Actions."
  exit 1
fi
gradle --no-daemon :app:assembleDebug
printf '\nAPK: %s\n' "$PWD/app/build/outputs/apk/debug/app-debug.apk"
