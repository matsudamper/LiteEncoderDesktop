#!/usr/bin/env bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/..}"

# セッション中の初回ビルドを待たずに済むよう、依存関係を事前に取得しておく
./gradlew --no-daemon -q compileKotlin
