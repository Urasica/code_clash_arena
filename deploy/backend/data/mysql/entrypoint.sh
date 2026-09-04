#!/bin/sh
set -eu
umask 077

runtime_dir=/tmp/cca-mysql-secrets
runtime_files="
mysql-root-password
mysql-app-password
mysql-migration-password
mysql-backup-password
mysql-health-password
mysql-app-client
mysql-migration-client
mysql-backup-client
mysql-health-client
"
mkdir -p "$runtime_dir"
chmod 750 "$runtime_dir"
for name in $runtime_files; do
    cp "/run/secrets/$name" "$runtime_dir/$name"
    chmod 440 "$runtime_dir/$name"
    chown mysql:root "$runtime_dir/$name"
done
chown mysql:root "$runtime_dir"

cp /opt/cca/cca.cnf /tmp/cca.cnf
chmod 600 /tmp/cca.cnf
chown mysql:mysql /tmp/cca.cnf
exec /usr/local/bin/docker-entrypoint.sh "$@"
