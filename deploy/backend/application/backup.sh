#!/usr/bin/env bash
set -euo pipefail

readonly DATACTL="${CCA_DATACTL:-/opt/code-clash-arena/backend/current/data/datactl.py}"
readonly SECRETS_DIR="${CCA_DATA_SECRETS_DIR:-/etc/code-clash-arena/data-secrets}"
readonly PROJECT="${CCA_DATA_PROJECT:-cca-data-production}"
readonly OUTBOX_ROOT="${CCA_BACKUP_OUTBOX:-/var/lib/code-clash-arena/backup-outbox}"
readonly RECEIPT_ROOT="${CCA_BACKUP_RECEIPTS:-/var/lib/code-clash-arena/backup-receipts}"
readonly PYTHON_BIN="${CCA_PYTHON_BIN:-/usr/bin/python3.11}"

fail() {
  printf 'pre-deployment backup failed: %s\n' "$1" >&2
  exit 1
}

for name in BACKUP_COMPARTMENT_ID BACKUP_NAMESPACE BACKUP_BUCKET BACKUP_AGE_RECIPIENT SOURCE_SHA; do
  [[ -n "${!name:-}" ]] || fail "$name is required"
done
[[ "$SOURCE_SHA" =~ ^[0-9a-f]{40}$ ]] || fail "SOURCE_SHA must be a full lowercase commit SHA"
[[ -f "$DATACTL" ]] || fail "data controller is missing"
[[ -x "$PYTHON_BIN" ]] || fail "Python 3.11 executable is unavailable"

mkdir -p "$OUTBOX_ROOT" "$RECEIPT_ROOT"
work_dir="$(mktemp -d "${OUTBOX_ROOT}/backup.XXXXXX")"
cleanup() {
  rm -rf -- "$work_dir"
}
trap cleanup EXIT
backup_file="${work_dir}/snapshot.sql.age"
backup_json="${work_dir}/backup.json"
upload_json="${work_dir}/upload.json"

"$PYTHON_BIN" "$DATACTL" backup --project "$PROJECT" --secrets "$SECRETS_DIR" \
  --recipient "$BACKUP_AGE_RECIPIENT" --output "$backup_file" > "$backup_json"

mapfile -t backup_fields < <("$PYTHON_BIN" - "$backup_json" <<'PY'
import json
import sys
from pathlib import Path

value = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
if value.get("result") != "encrypted":
    raise SystemExit("backup receipt is invalid")
print(value["object_name"])
print(value["sha256"])
PY
)
[[ "${#backup_fields[@]}" -eq 2 ]] || fail "backup receipt is incomplete"

"$PYTHON_BIN" "$DATACTL" upload --file "$backup_file" --region ap-tokyo-1 \
  --compartment-id "$BACKUP_COMPARTMENT_ID" --namespace "$BACKUP_NAMESPACE" \
  --bucket "$BACKUP_BUCKET" --object-name "${backup_fields[0]}" > "$upload_json"

deployment_id="${OCI_DEVOPS_DEPLOYMENT_ID:-manual}"
"$PYTHON_BIN" - "$backup_json" "$upload_json" "$RECEIPT_ROOT" "$SOURCE_SHA" "$deployment_id" <<'PY'
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

backup_path, upload_path, receipt_root, source_sha, deployment_id = sys.argv[1:]
if not re.fullmatch(r"(?:manual|ocid1\.devopsdeployment\.[a-z0-9.-]+)", deployment_id):
    raise SystemExit("invalid deployment identity")
backup = json.loads(Path(backup_path).read_text(encoding="utf-8"))
upload = json.loads(Path(upload_path).read_text(encoding="utf-8"))
if upload.get("result") != "uploaded" or backup["sha256"] != upload["sha256"] or backup["object_name"] != upload["object_name"]:
    raise SystemExit("upload receipt does not match the encrypted backup")
receipt = {
    "backup": {key: upload[key] for key in ("bucket", "compartment_id", "object_name", "region", "sha256")},
    "created_at": datetime.now(timezone.utc).isoformat(),
    "deployment_id": deployment_id,
    "source_sha": source_sha,
}
target = Path(receipt_root) / f"{deployment_id.replace('.', '_')}.json"
descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o640)
with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
    json.dump(receipt, stream, indent=2, sort_keys=True)
    stream.write("\n")
print(json.dumps({"result": "recorded", "source_sha": source_sha, "object_name": upload["object_name"]}))
PY
