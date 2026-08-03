#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/.verify"
mkdir -p "$OUT/jenkins-stubs" "$OUT/plugin"

javac --release 17 \
  -d "$OUT/jenkins-stubs" \
  $(find "$ROOT/verification/jenkins-stubs" -name '*.java' | sort)

javac --release 17 \
  -cp "$OUT/api:$OUT/core:$OUT/collectors:$OUT/jenkins-stubs" \
  -d "$OUT/plugin" \
  $(find "$ROOT/telemetry-jenkins-plugin/src/main/java" -name '*.java' | sort)

mkdir -p "$OUT/plugin-harness-test"

javac --release 17 \
  -cp "$OUT/api:$OUT/core:$OUT/collectors:$OUT/jenkins-stubs:$OUT/plugin" \
  -d "$OUT/plugin-harness-test" \
  $(find "$ROOT/telemetry-jenkins-plugin/src/test/java" -name '*.java' | sort)

mkdir -p "$OUT/plugin-test"

javac --release 17 \
  -cp "$OUT/api:$OUT/core:$OUT/collectors:$OUT/jenkins-stubs:$OUT/plugin" \
  -d "$OUT/plugin-test" \
  $(find "$ROOT/verification/plugin-src" -name '*.java' | sort)

java -cp "$OUT/api:$OUT/core:$OUT/collectors:$OUT/jenkins-stubs:$OUT/plugin:$OUT/plugin-test" \
  io.jenkins.telemetry.jenkins.PluginSourceSelfTest

echo "Jenkins adapter and Test Harness sources compiled against structural API stubs."
