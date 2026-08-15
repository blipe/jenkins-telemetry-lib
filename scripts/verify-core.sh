#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/.verify"
rm -rf "$OUT"
mkdir -p "$OUT/api" "$OUT/core" "$OUT/collectors" "$OUT/test"

javac --release 17 \
  -d "$OUT/api" \
  $(find "$ROOT/telemetry-api/src/main/java" -name '*.java' | sort)

javac --release 17 \
  -cp "$OUT/api" \
  -d "$OUT/core" \
  $(find "$ROOT/telemetry-core/src/main/java" -name '*.java' | sort)

javac --release 17 \
  -cp "$OUT/api:$OUT/core" \
  -d "$OUT/collectors" \
  $(find \
    "$ROOT/telemetry-collector-file/src/main/java" \
    "$ROOT/telemetry-collector-http-json/src/main/java" \
    "$ROOT/telemetry-collector-otlp-json/src/main/java" \
    -name '*.java' | sort)

javac --release 17 --add-modules jdk.httpserver \
  -cp "$OUT/api:$OUT/core:$OUT/collectors" \
  -d "$OUT/test" \
  $(find "$ROOT/verification/src/main/java" -name '*.java' | sort)

java --add-modules jdk.httpserver \
  -cp "$OUT/api:$OUT/core:$OUT/collectors:$OUT/test" \
  io.jenkins.telemetry.verification.CoreSelfTest
