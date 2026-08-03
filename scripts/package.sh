#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$ROOT/../jenkins-telemetry-lib.zip}"

"$ROOT/scripts/verify-all.sh"

if command -v mvn >/dev/null 2>&1; then
  (cd "$ROOT" && mvn -DskipTests=false clean verify)
else
  echo "Maven is not installed; dependency-free and structural verification passed."
  echo "Run 'mvn clean verify' in a networked environment to execute Jenkins Test Harness tests and build the HPI."
fi

rm -rf "$ROOT/.verify"
find "$ROOT" -type d \( -name target -o -name .git \) -prune -exec rm -rf {} + 2>/dev/null || true

(
  cd "$ROOT"
  find . -type f \
    ! -path './.verify/*' \
    ! -path '*/target/*' \
    ! -path './.git/*' \
    ! -name 'SOURCE-MANIFEST.txt' \
    -print | sort | sed 's#^./##' > SOURCE-MANIFEST.txt
)

python3 - "$ROOT" "$OUT" <<'PY'
from pathlib import Path
import sys, zipfile
root = Path(sys.argv[1]).resolve()
out = Path(sys.argv[2]).resolve()
out.parent.mkdir(parents=True, exist_ok=True)
if out.exists(): out.unlink()
with zipfile.ZipFile(out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for path in sorted(root.rglob('*')):
        rel = path.relative_to(root)
        if any(part in {'.verify', 'target', '.git'} for part in rel.parts):
            continue
        if path.is_file():
            z.write(path, Path(root.name) / rel)
print(out)
PY
