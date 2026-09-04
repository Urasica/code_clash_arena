#!/bin/sh
set -eu

program=${1:-}
role=${2:-}
shift 2 || true
case "$program" in mysql|mysqladmin|mysqldump) ;;
    (*) echo "Unsupported MySQL client program." >&2; exit 2 ;;
esac
case "$role" in app|migration|backup|health) ;;
    (*) echo "Unsupported MySQL credential role." >&2; exit 2 ;;
esac

source_file="/tmp/cca-mysql-secrets/mysql-${role}-client"
target_file="/tmp/cca-mysql-${role}-$$.cnf"
umask 077
cp "$source_file" "$target_file"
chmod 600 "$target_file"
trap 'rm -f "$target_file"' EXIT HUP INT TERM
"$program" "--defaults-extra-file=$target_file" "$@"
