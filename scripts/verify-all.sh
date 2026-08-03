#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/scripts/verify-core.sh"
"$ROOT/scripts/verify-plugin-source.sh"
