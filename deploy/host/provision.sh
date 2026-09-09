#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPOSITORY_ROOT="$(cd "${SCRIPT_ROOT}/../.." && pwd)"
readonly ROLE="${1:-}"
readonly PYTHON_BIN="${CCA_PYTHON_BIN:-/usr/bin/python3.11}"
readonly PUBLIC_ORIGIN="${CCA_PUBLIC_ORIGIN:-}"

fail() {
  printf 'host provisioning failed: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is unavailable: $1"
}

install_selinux_context() {
  local expression="$1"
  local context="$2"
  semanage fcontext -a -t "$context" "$expression" 2>/dev/null || \
    semanage fcontext -m -t "$context" "$expression"
}

[[ "$ROLE" == "edge" || "$ROLE" == "application" ]] || fail "usage: provision.sh edge|application"
[[ "$EUID" -eq 0 ]] || fail "run the one-time provisioning command as root"
[[ "$(uname -m)" == "x86_64" ]] || fail "the deployment profile requires native AMD64"
readonly OPERATING_SYSTEM_ID="$(. /etc/os-release && printf '%s' "$ID")"
readonly OPERATING_SYSTEM_MAJOR="$(. /etc/os-release && printf '%s' "${VERSION_ID%%.*}")"
[[ "$OPERATING_SYSTEM_ID" == "ol" && "$OPERATING_SYSTEM_MAJOR" == "9" ]] || \
  fail "the deployment profile requires Oracle Linux 9"
[[ -x "$PYTHON_BIN" ]] || fail "Python 3.11 must be installed at $PYTHON_BIN"
"$PYTHON_BIN" - <<'PY'
import sys

if sys.version_info[:2] != (3, 11):
    raise SystemExit("the deployment profile requires Python 3.11")
PY

for command in nginx openssl systemctl firewall-cmd semanage restorecon setsebool visudo; do
  require_command "$command"
done
id ocarun >/dev/null 2>&1 || fail "ocarun is missing; enable the Oracle Cloud Agent Run Command plugin first"

install -d -o ocarun -g ocarun -m 0750 /opt/code-clash-arena/staging
install -d -m 0755 /opt/code-clash-arena
setsebool -P httpd_can_network_connect 1

if [[ "$ROLE" == "edge" ]]; then
  [[ -n "$PUBLIC_ORIGIN" ]] || fail "CCA_PUBLIC_ORIGIN is required for the Edge host"
  [[ -s /etc/pki/code-clash-arena/public/fullchain.pem ]] || fail "public TLS certificate is missing"
  [[ -s /etc/pki/code-clash-arena/public/private.key ]] || fail "public TLS private key is missing"
  [[ -s /etc/pki/code-clash-arena/internal/ca.pem ]] || fail "private application CA is missing"
  getent ahostsv4 app.cca.internal >/dev/null || fail "app.cca.internal must resolve to the Application VM private address"
  public_hostname="$("$PYTHON_BIN" - "$PUBLIC_ORIGIN" <<'PY'
import ipaddress
import re
import sys
from urllib.parse import urlsplit

value = sys.argv[1]
parsed = urlsplit(value)
hostname = parsed.hostname or ""
try:
    ipaddress.ip_address(hostname)
    is_ip = True
except ValueError:
    is_ip = False
labels = hostname.split(".")
valid_labels = all(
    re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", label)
    for label in labels
)
if (
    parsed.scheme != "https"
    or parsed.username is not None
    or parsed.password is not None
    or parsed.netloc.lower() != hostname.lower()
    or parsed.path not in {"", "/"}
    or parsed.query
    or parsed.fragment
    or is_ip
    or len(labels) < 2
    or len(hostname) > 253
    or not valid_labels
):
    raise SystemExit("CCA_PUBLIC_ORIGIN must be a bare HTTPS DNS origin")
print(hostname.lower())
PY
)" || fail "CCA_PUBLIC_ORIGIN is invalid"
  openssl x509 -in /etc/pki/code-clash-arena/public/fullchain.pem \
    -noout -checkhost "$public_hostname" >/dev/null || fail "public certificate does not match CCA_PUBLIC_ORIGIN"

  install -d -o ocarun -g ocarun -m 0750 /opt/code-clash-arena/staging/frontend
  install -d -o ocarun -g ocarun -m 0755 /opt/code-clash-arena/frontend
  install -d -o ocarun -g ocarun -m 0750 /var/lib/code-clash-arena/deployments/frontend
  readonly edge_template="${REPOSITORY_ROOT}/deploy/frontend/nginx/code-clash-arena.conf"
  rendered_edge_config="$(mktemp)"
  "$PYTHON_BIN" - "$edge_template" "$rendered_edge_config" "$public_hostname" <<'PY'
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
hostname = sys.argv[3]
template = source.read_text(encoding="utf-8")
token = "__CCA_PUBLIC_HOSTNAME__"
if template.count(token) < 2:
    raise SystemExit("Edge Nginx hostname template is incomplete")
target.write_text(template.replace(token, hostname), encoding="utf-8", newline="\n")
PY
  install -o root -g root -m 0644 "$rendered_edge_config" /etc/nginx/conf.d/code-clash-arena.conf
  rm -f -- "$rendered_edge_config"
  install -o root -g root -m 0440 \
    "${REPOSITORY_ROOT}/deploy/frontend/sudoers/code-clash-arena-frontend" \
    /etc/sudoers.d/code-clash-arena-frontend
  visudo -cf /etc/sudoers.d/code-clash-arena-frontend >/dev/null
  install_selinux_context '/opt/code-clash-arena/frontend(/.*)?' httpd_sys_content_t
  restorecon -RF /opt/code-clash-arena/frontend /etc/nginx/conf.d/code-clash-arena.conf
  firewall-cmd --permanent --add-service=http >/dev/null
  firewall-cmd --permanent --add-service=https >/dev/null
else
  for command in age docker java oci; do require_command "$command"; done
  [[ -s /etc/pki/code-clash-arena/application/fullchain.pem ]] || fail "internal TLS certificate is missing"
  [[ -s /etc/pki/code-clash-arena/application/private.key ]] || fail "internal TLS private key is missing"
  [[ -s /etc/code-clash-arena/backend.env ]] || fail "populated backend environment file is missing"
  [[ -d /etc/code-clash-arena/data-secrets/backend ]] || fail "DATA-03 secret bundle is missing"

  getent group docker >/dev/null || fail "docker group is missing"
  if ! id cca >/dev/null 2>&1; then
    useradd --system --home-dir /nonexistent --shell /sbin/nologin cca
  fi
  usermod -aG docker cca
  chown root:cca /etc/code-clash-arena/backend.env
  chmod 0640 /etc/code-clash-arena/backend.env
  chown -R cca:cca /etc/code-clash-arena/data-secrets/backend
  chmod 0700 /etc/code-clash-arena/data-secrets/backend
  find /etc/code-clash-arena/data-secrets/backend -type f -exec chmod 0600 {} +

  install -d -o ocarun -g ocarun -m 0750 /opt/code-clash-arena/staging/backend
  install -d -o root -g cca -m 0750 /opt/code-clash-arena/backend
  install -d -o cca -g cca -m 0750 /var/lib/code-clash-arena/workspaces
  install -d -o root -g root -m 0750 /var/lib/code-clash-arena/backup-outbox
  install -d -o root -g root -m 0750 /var/lib/code-clash-arena/backup-receipts
  install -d -o root -g root -m 0750 /var/lib/code-clash-arena/deployments/backend
  install -o root -g root -m 0644 \
    "${REPOSITORY_ROOT}/deploy/backend/application/nginx/code-clash-arena-application.conf" \
    /etc/nginx/conf.d/code-clash-arena-application.conf
  install -o root -g root -m 0644 \
    "${REPOSITORY_ROOT}/deploy/backend/application/systemd/code-clash-arena-backend.service" \
    /etc/systemd/system/code-clash-arena-backend.service
  install_selinux_context '/var/lib/code-clash-arena/workspaces(/.*)?' container_file_t
  restorecon -RF /var/lib/code-clash-arena/workspaces /etc/nginx/conf.d/code-clash-arena-application.conf
  firewall-cmd --permanent --add-port=8443/tcp >/dev/null
  systemctl daemon-reload
  systemctl enable docker.service nginx.service >/dev/null
fi

firewall-cmd --reload >/dev/null
nginx -t
systemctl restart nginx
printf 'host provisioning passed for role=%s\n' "$ROLE"
