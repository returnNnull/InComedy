#!/bin/sh
set -eu

if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" = "YES" ]; then
  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES"
  exit 0
fi

if [ -z "${JAVA_HOME:-}" ] && [ -x "/usr/libexec/java_home" ]; then
  JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
fi

if [ -z "${JAVA_HOME:-}" ] && [ -x "/opt/homebrew/opt/openjdk/bin/java" ]; then
  JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
fi

if [ -z "${JAVA_HOME:-}" ]; then
  echo "JAVA_HOME is not set. Install JDK or configure JAVA_HOME in Xcode build settings."
  exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$PROJECT_ROOT"
./gradlew :shared:embedAndSignAppleFrameworkForXcode
