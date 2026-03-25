#!/bin/sh
set -eu

if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" = "YES" ]; then
  echo "OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES is set, but Kotlin framework build will still run to avoid stale Shared.framework."
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

# Для Xcode script phase используем отдельный persistent cache внутри репозитория,
# чтобы KMP build не зависел от случайного состояния глобального Gradle home.
if [ -z "${GRADLE_USER_HOME:-}" ]; then
  GRADLE_USER_HOME="$PROJECT_ROOT/.gradle/xcode"
fi

export GRADLE_USER_HOME

cd "$PROJECT_ROOT"

# Для Kotlin/Native сначала bootstrap-им writable compiler bundle в локальный cache.
# Иначе sandbox может упираться либо в lock внутри `~/.konan`, либо в duplicate
# platform KLIB-ы, если Gradle частично смешивает глобальный и redirected cache.
KOTLIN_NATIVE_BOOTSTRAP_ROOT="${INCOMEDY_KONAN_BOOTSTRAP_ROOT:-$GRADLE_USER_HOME/konan-bootstrap}"
KOTLIN_NATIVE_HOME="${INCOMEDY_KOTLIN_NATIVE_HOME:-}"

mkdir -p "$GRADLE_USER_HOME" "$KOTLIN_NATIVE_BOOTSTRAP_ROOT"

if [ -z "$KOTLIN_NATIVE_HOME" ]; then
  KOTLIN_NATIVE_HOME="$(find "$KOTLIN_NATIVE_BOOTSTRAP_ROOT" -maxdepth 1 -type d -name 'kotlin-native-prebuilt-*' -print -quit 2>/dev/null || true)"
fi

if [ -z "$KOTLIN_NATIVE_HOME" ] || [ ! -d "$KOTLIN_NATIVE_HOME/klib/platform" ]; then
  # Первый прогон может скачать/распаковать bundle, но не должен заходить в compile.
  ./gradlew --no-daemon --console=plain -Pkonan.data.dir="$KOTLIN_NATIVE_BOOTSTRAP_ROOT" :core:common:downloadKotlinNativeDistribution
  KOTLIN_NATIVE_HOME="$(find "$KOTLIN_NATIVE_BOOTSTRAP_ROOT" -maxdepth 1 -type d -name 'kotlin-native-prebuilt-*' -print -quit 2>/dev/null || true)"
fi

if [ -z "$KOTLIN_NATIVE_HOME" ] || [ ! -d "$KOTLIN_NATIVE_HOME/klib/platform" ]; then
  echo "Writable Kotlin/Native bundle bootstrap failed for Xcode build."
  exit 1
fi

export KOTLIN_NATIVE_HOME

# В Xcode script phase daemon не нужен: отдельный одноразовый запуск снижает риск
# зависших background-процессов и повторного конфликта между retry-попытками.
./gradlew --no-daemon --console=plain -Pkonan.data.dir="$KOTLIN_NATIVE_BOOTSTRAP_ROOT" -Pkotlin.native.home="$KOTLIN_NATIVE_HOME" :shared:embedAndSignAppleFrameworkForXcode
