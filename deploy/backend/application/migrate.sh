#!/usr/bin/env bash
set -euo pipefail

readonly ACTION="${1:-}"
readonly MIGRATION_IMAGE="${MIGRATION_IMAGE:-}"
readonly MIGRATION_PASSWORD_FILE="${CCA_DATA_SECRETS_DIR:-/etc/code-clash-arena/data-secrets}/mysql-migration-password"
readonly MIGRATIONS_DIR="${CCA_MIGRATIONS_DIR:-/opt/code-clash-arena/backend/current/migrations}"

fail() {
  printf 'migration failed: %s\n' "$1" >&2
  exit 1
}

[[ "$ACTION" == "plan" || "$ACTION" == "apply" ]] || fail "usage: migrate.sh plan|apply"
[[ "$MIGRATION_IMAGE" =~ ^[^@[:space:]]+@sha256:[0-9a-f]{64}$ ]] || fail "MIGRATION_IMAGE must be digest-pinned"
[[ -d "$MIGRATIONS_DIR" ]] || fail "migration directory is missing"
[[ -f "$MIGRATION_PASSWORD_FILE" && ! -L "$MIGRATION_PASSWORD_FILE" ]] || fail "migration credential file is missing or linked"

config_file="$(mktemp /dev/shm/cca-flyway.XXXXXX.conf)"
cleanup() {
  rm -f -- "$config_file"
}
trap cleanup EXIT
chmod 600 "$config_file"
{
  printf '%s\n' 'flyway.url=jdbc:mysql://127.0.0.1:3306/code_arena?sslMode=REQUIRED&allowPublicKeyRetrieval=false&serverTimezone=UTC'
  printf '%s\n' 'flyway.user=cca_migrator'
  printf 'flyway.password='
  tr -d '\r\n' < "$MIGRATION_PASSWORD_FILE"
  printf '\n%s\n' 'flyway.locations=filesystem:/flyway/sql'
  printf '%s\n' 'flyway.validateMigrationNaming=true'
} > "$config_file"

run_flyway() {
  docker run --rm --network host --read-only --tmpfs /tmp:rw,noexec,nosuid,size=32m \
    --cap-drop ALL --security-opt no-new-privileges \
    --mount "type=bind,src=${config_file},dst=/flyway/conf/cca.conf,readonly" \
    --mount "type=bind,src=${MIGRATIONS_DIR},dst=/flyway/sql,readonly" \
    "$MIGRATION_IMAGE" -configFiles=/flyway/conf/cca.conf "$1"
}

if [[ "$ACTION" == "plan" ]]; then
  run_flyway info
  run_flyway validate
else
  run_flyway migrate
  run_flyway validate
fi
