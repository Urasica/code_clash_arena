#!/usr/bin/env bash
set -euo pipefail

readonly RELEASE_ROOT="${CCA_BACKEND_RELEASE_ROOT:-/opt/code-clash-arena/backend}"
readonly STAGING_ROOT="${CCA_BACKEND_STAGING_ROOT:-/opt/code-clash-arena/staging/backend}"
readonly STATE_ROOT="${CCA_BACKEND_STATE_ROOT:-/var/lib/code-clash-arena/deployments/backend}"
readonly SECRETS_DIR="${CCA_DATA_SECRETS_DIR:-/etc/code-clash-arena/data-secrets}"
readonly DATA_PROJECT="${CCA_DATA_PROJECT:-cca-data-production}"
readonly RELEASECTL="${STAGING_ROOT}/ops/releasectl.py"
readonly PYTHON_BIN="${CCA_PYTHON_BIN:-/usr/bin/python3.11}"

fail() {
  printf 'backend deployment failed: %s\n' "$1" >&2
  exit 1
}

require_root() {
  [[ "${EUID}" -eq 0 ]] || fail "the OCI Run Command deployment step must run as root"
}

metadata() {
  "$PYTHON_BIN" "$RELEASECTL" metadata --root "$1" --component backend --source-sha "$2" --key "$3"
}

validate_runtime_environment() {
  "$PYTHON_BIN" - "$1" "$2" "$3" "$4" "$5" "$6" <<'PY'
import sys
from pathlib import Path

path, engine_image, migration_image, mysql_image, redis_image, policy_version = sys.argv[1:]
expected = {
    "DATA_MYSQL_IMAGE": mysql_image,
    "DATA_REDIS_IMAGE": redis_image,
    "ENGINE_IMAGE": engine_image,
    "ENGINE_POLICY_VERSION": policy_version,
    "MIGRATION_IMAGE": migration_image,
}
actual = {}
for line in Path(path).read_text(encoding="utf-8").splitlines():
    if not line or line.startswith("#") or "=" not in line:
        raise SystemExit("runtime environment contains an invalid line")
    key, value = line.split("=", 1)
    if key in actual:
        raise SystemExit("runtime environment contains a duplicate key")
    actual[key] = value
if actual != expected:
    raise SystemExit("runtime environment does not match release metadata")
PY
}

record_receipt() {
  local source_sha="$1"
  local engine_image="$2"
  local policy_version="$3"
  local deployment_id="${OCI_DEVOPS_DEPLOYMENT_ID:-manual}"
  mkdir -p "$STATE_ROOT"
  "$PYTHON_BIN" - "$STATE_ROOT" "$source_sha" "$engine_image" "$policy_version" "$deployment_id" <<'PY'
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

state_root, source_sha, engine_image, policy_version, deployment_id = sys.argv[1:]
if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
    raise SystemExit("invalid receipt source SHA")
if not re.fullmatch(r"(?:manual|ocid1\.devopsdeployment\.[a-z0-9.-]+)", deployment_id):
    raise SystemExit("invalid OCI deployment identity")
if not re.fullmatch(r"[^@\s]+@sha256:[0-9a-f]{64}", engine_image):
    raise SystemExit("invalid receipt engine image")
receipt = {
    "component": "backend",
    "deployed_at": datetime.now(timezone.utc).isoformat(),
    "deployment_id": deployment_id,
    "engine_image": engine_image,
    "engine_policy_version": policy_version,
    "source_sha": source_sha,
    "target": "linux/amd64",
}
target = Path(state_root) / f"{deployment_id.replace('.', '_')}.json"
descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o640)
with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
    json.dump(receipt, stream, indent=2, sort_keys=True)
    stream.write("\n")
PY
}

start_data() {
  "$PYTHON_BIN" "${RELEASE_ROOT}/current/data/datactl.py" validate-configuration \
    --project "$DATA_PROJECT" --secrets "$SECRETS_DIR"
  "$PYTHON_BIN" "${RELEASE_ROOT}/current/data/datactl.py" start \
    --project "$DATA_PROJECT" --secrets "$SECRETS_DIR"
}

deploy() {
  require_root
  [[ -x "$PYTHON_BIN" ]] || fail "Python 3.11 executable is unavailable"
  local source_sha="${SOURCE_SHA:-}"
  [[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || fail "SOURCE_SHA must be a full lowercase commit SHA"
  [[ -f "$RELEASECTL" ]] || fail "release controller is missing from staging"

  "$PYTHON_BIN" "$RELEASECTL" verify --root "$STAGING_ROOT" --component backend --source-sha "$source_sha"
  local engine_image migration_image mysql_image redis_image policy_version
  engine_image="$(metadata "$STAGING_ROOT" "$source_sha" engine_image)"
  migration_image="$(metadata "$STAGING_ROOT" "$source_sha" migration_image)"
  mysql_image="$(metadata "$STAGING_ROOT" "$source_sha" data_mysql_image)"
  redis_image="$(metadata "$STAGING_ROOT" "$source_sha" data_redis_image)"
  policy_version="$(metadata "$STAGING_ROOT" "$source_sha" engine_policy_version)"
  validate_runtime_environment "${STAGING_ROOT}/runtime.env" "$engine_image" "$migration_image" \
    "$mysql_image" "$redis_image" "$policy_version"

  mkdir -p "$STATE_ROOT"
  local current_sha=""
  if [[ -L "${RELEASE_ROOT}/current" ]]; then
    current_sha="$(basename "$(readlink -f "${RELEASE_ROOT}/current")")"
  fi
  local deployment_id="${OCI_DEVOPS_DEPLOYMENT_ID:-manual}"
  [[ "$deployment_id" =~ ^(manual|ocid1\.devopsdeployment\.[a-z0-9.-]+)$ ]] || fail "invalid OCI deployment identity"
  local marker="${STATE_ROOT}/pending-${deployment_id//./_}"

  docker pull "$engine_image"
  docker pull "$migration_image"
  docker pull "$mysql_image"
  docker pull "$redis_image"
  "$PYTHON_BIN" "$RELEASECTL" install \
    --staging "$STAGING_ROOT" --release-root "$RELEASE_ROOT" \
    --component backend --source-sha "$source_sha"
  if [[ "$current_sha" != "$source_sha" ]]; then
    (umask 027 && printf '%s\n' "$source_sha" > "$marker")
  fi

  export MYSQL_IMAGE="$mysql_image" REDIS_IMAGE="$redis_image"
  start_data
  export SOURCE_SHA="$source_sha" MIGRATION_IMAGE="$migration_image"
  export CCA_DATACTL="${RELEASE_ROOT}/current/data/datactl.py"
  export CCA_MIGRATIONS_DIR="${RELEASE_ROOT}/current/migrations"
  export CCA_DATA_SECRETS_DIR="$SECRETS_DIR" CCA_DATA_PROJECT="$DATA_PROJECT"
  "${RELEASE_ROOT}/current/ops/migrate.sh" plan
  "${RELEASE_ROOT}/current/ops/backup.sh"
  "${RELEASE_ROOT}/current/ops/migrate.sh" apply

  /usr/bin/systemctl restart code-clash-arena-backend.service
  "$PYTHON_BIN" "${RELEASE_ROOT}/current/ops/verify.py" --engine-image "$engine_image"
  record_receipt "$source_sha" "$engine_image" "$policy_version"
  rm -f -- "$marker"
}

rollback() {
  require_root
  [[ -x "$PYTHON_BIN" ]] || fail "Python 3.11 executable is unavailable"
  local deployment_id="${OCI_DEVOPS_DEPLOYMENT_ID:-manual}"
  local marker="${STATE_ROOT}/pending-${deployment_id//./_}"
  if [[ "${ROLLBACK_ONLY_IF_PENDING:-false}" == true && ! -f "$marker" ]]; then
    printf 'backend rollback skipped: deployment did not activate a new release\n'
    return
  fi
  local current_releasectl="${RELEASE_ROOT}/current/ops/releasectl.py"
  [[ -f "$current_releasectl" ]] || fail "release controller is missing from current release"
  "$PYTHON_BIN" "$current_releasectl" rollback --release-root "$RELEASE_ROOT" --component backend
  rm -f -- "$marker"
  local previous_sha engine_image mysql_image redis_image
  previous_sha="$(basename "$(readlink -f "${RELEASE_ROOT}/current")")"
  engine_image="$("$PYTHON_BIN" "${RELEASE_ROOT}/current/ops/releasectl.py" metadata \
    --root "${RELEASE_ROOT}/current" --component backend --source-sha "$previous_sha" --key engine_image)"
  mysql_image="$("$PYTHON_BIN" "${RELEASE_ROOT}/current/ops/releasectl.py" metadata \
    --root "${RELEASE_ROOT}/current" --component backend --source-sha "$previous_sha" --key data_mysql_image)"
  redis_image="$("$PYTHON_BIN" "${RELEASE_ROOT}/current/ops/releasectl.py" metadata \
    --root "${RELEASE_ROOT}/current" --component backend --source-sha "$previous_sha" --key data_redis_image)"
  export MYSQL_IMAGE="$mysql_image" REDIS_IMAGE="$redis_image"
  start_data
  docker pull "$engine_image"
  /usr/bin/systemctl restart code-clash-arena-backend.service
  "$PYTHON_BIN" "${RELEASE_ROOT}/current/ops/verify.py" --engine-image "$engine_image"
}

case "${1:-}" in
  deploy) deploy ;;
  rollback) rollback ;;
  *) fail "usage: install.sh deploy|rollback" ;;
esac
