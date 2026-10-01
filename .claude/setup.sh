#!/usr/bin/env bash
# SessionStart hook: Claude Code on the web でビルドできる状態を整える
set -euo pipefail

# ローカル実行時は何もしない
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/..}"

# build.gradle.kts の jvmToolchain(21) に合わせて JDK 21 を要求する
if ! command -v java >/dev/null 2>&1; then
  echo "java not found. JDK 21 is required." >&2
  exit 1
fi
java_version=$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')
if [ "$java_version" != "21" ]; then
  echo "JDK 21 is required, but found Java ${java_version:-unknown}." >&2
  exit 1
fi

# Gradle本体と依存関係を事前取得し、コンパイルまで通しておく
./gradlew --no-daemon -q compileKotlin
