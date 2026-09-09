#!/usr/bin/env bash
set -euo pipefail

readonly RELEASE_ROOT="${CCA_FRONTEND_RELEASE_ROOT:-/opt/code-clash-arena/frontend}"
readonly STAGING_ROOT="${CCA_FRONTEND_STAGING_ROOT:-/opt/code-clash-arena/staging/frontend}"
readonly STATE_ROOT="${CCA_FRONTEND_STATE_ROOT:-/var/lib/code-clash-arena/deployments/frontend}"
readonly RELEASECTL="${STAGING_ROOT}/ops/releasectl.py"
readonly PYTHON_BIN="${CCA_PYTHON_BIN:-/usr/bin/python3.11}"

fail() {
  printf 'frontend deployment failed: %s\n' "$1" >&2
  exit 1
}

record_receipt() {
  local source_sha="$1"
  local manifest="${RELEASE_ROOT}/current/release-manifest.json"
  local deployment_id="${OCI_DEVOPS_DEPLOYMENT_ID:-manual}"
  mkdir -p "${STATE_ROOT}"
  "$PYTHON_BIN" - "${manifest}" "${STATE_ROOT}" "${source_sha}" "${deployment_id}" <<'PY'
import json
import os
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

manifest_path, state_root, source_sha, deployment_id = sys.argv[1:]
if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
    raise SystemExit("invalid receipt source SHA")
if not re.fullmatch(r"(?:manual|ocid1\.devopsdeployment\.[a-z0-9.-]+)", deployment_id):
    raise SystemExit("invalid OCI deployment identity")
manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
receipt = {
    "component": "frontend",
    "deployed_at": datetime.now(timezone.utc).isoformat(),
    "deployment_id": deployment_id,
    "source_sha": source_sha,
    "target": manifest["target"],
}
target = Path(state_root) / f"{deployment_id.replace('.', '_')}.json"
descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o640)
with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
    json.dump(receipt, stream, indent=2, sort_keys=True)
    stream.write("\n")
PY
}

deploy() {
  [[ -x "$PYTHON_BIN" ]] || fail "Python 3.11 executable is unavailable"
  local source_sha="${SOURCE_SHA:-}"
  local public_origin="${PUBLIC_ORIGIN:-}"
  [[ "$source_sha" =~ ^[0-9a-f]{40}$ ]] || fail "SOURCE_SHA must be a full lowercase commit SHA"
  [[ "$public_origin" == https://* ]] || fail "PUBLIC_ORIGIN must use HTTPS"
  [[ -f "$RELEASECTL" ]] || fail "release controller is missing from staging"

  local manifest_origin
  manifest_origin="$("$PYTHON_BIN" "$RELEASECTL" metadata --root "$STAGING_ROOT" --component frontend --source-sha "$source_sha" --key public_origin)"
  [[ "$manifest_origin" == "${public_origin%/}" ]] || fail "artifact origin does not match the deployment environment"

  mkdir -p "$STATE_ROOT"
  local current_sha=""
  if [[ -L "${RELEASE_ROOT}/current" ]]; then
    current_sha="$(basename "$(readlink -f "${RELEASE_ROOT}/current")")"
  fi
  local deployment_id="${OCI_DEVOPS_DEPLOYMENT_ID:-manual}"
  [[ "$deployment_id" =~ ^(manual|ocid1\.devopsdeployment\.[a-z0-9.-]+)$ ]] || fail "invalid OCI deployment identity"
  local marker="${STATE_ROOT}/pending-${deployment_id//./_}"

  "$PYTHON_BIN" "$RELEASECTL" install \
    --staging "$STAGING_ROOT" --release-root "$RELEASE_ROOT" \
    --component frontend --source-sha "$source_sha"
  if [[ "$current_sha" != "$source_sha" ]]; then
    (umask 027 && printf '%s\n' "$source_sha" > "$marker")
  fi
  sudo -n /usr/bin/nginx -t
  sudo -n /usr/bin/systemctl reload nginx
  "$PYTHON_BIN" "${RELEASE_ROOT}/current/ops/verify.py" --origin "$public_origin"
  record_receipt "$source_sha"
  rm -f -- "$marker"
}

rollback() {
  [[ -x "$PYTHON_BIN" ]] || fail "Python 3.11 executable is unavailable"
  local deployment_id="${OCI_DEVOPS_DEPLOYMENT_ID:-manual}"
  local marker="${STATE_ROOT}/pending-${deployment_id//./_}"
  if [[ "${ROLLBACK_ONLY_IF_PENDING:-false}" == true && ! -f "$marker" ]]; then
    printf 'frontend rollback skipped: deployment did not activate a new release\n'
    return
  fi
  local current_releasectl="${RELEASE_ROOT}/current/ops/releasectl.py"
  [[ -f "$current_releasectl" ]] || fail "release controller is missing from current release"
  "$PYTHON_BIN" "$current_releasectl" rollback --release-root "$RELEASE_ROOT" --component frontend
  rm -f -- "$marker"
  sudo -n /usr/bin/nginx -t
  sudo -n /usr/bin/systemctl reload nginx
  local previous_origin
  previous_origin="$("$PYTHON_BIN" "${RELEASE_ROOT}/current/ops/releasectl.py" metadata \
    --root "${RELEASE_ROOT}/current" --component frontend \
    --source-sha "$(basename "$(readlink -f "${RELEASE_ROOT}/current")")" --key public_origin)"
  "$PYTHON_BIN" "${RELEASE_ROOT}/current/ops/verify.py" --origin "$previous_origin"
}

case "${1:-}" in
  deploy) deploy ;;
  rollback) rollback ;;
  *) fail "usage: install.sh deploy|rollback" ;;
esac
