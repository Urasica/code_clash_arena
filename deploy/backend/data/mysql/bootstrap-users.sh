#!/bin/sh
set -eu

read_secret() {
    value=$(cat "/tmp/cca-mysql-secrets/$1")
    case "$value" in
        (*[!0-9a-f]*|'') echo "Invalid generated secret file: $1" >&2; exit 1 ;;
    esac
    if [ "${#value}" -ne 64 ]; then
        echo "Invalid generated secret length: $1" >&2
        exit 1
    fi
    printf '%s' "$value"
}

app_password=$(read_secret mysql-app-password)
migration_password=$(read_secret mysql-migration-password)
backup_password=$(read_secret mysql-backup-password)
health_password=$(read_secret mysql-health-password)
MYSQL_PWD=$(read_secret mysql-root-password)
export MYSQL_PWD

mysql --protocol=socket --user=root <<-EOSQL
CREATE USER 'cca_app'@'%' IDENTIFIED BY '${app_password}' REQUIRE SSL;
CREATE USER 'cca_migrator'@'%' IDENTIFIED BY '${migration_password}' REQUIRE SSL;
CREATE USER 'cca_backup'@'%' IDENTIFIED BY '${backup_password}' REQUIRE SSL;
CREATE USER 'cca_health'@'127.0.0.1' IDENTIFIED BY '${health_password}' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE ON code_arena.* TO 'cca_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, CREATE VIEW, SHOW VIEW, TRIGGER ON code_arena.* TO 'cca_migrator'@'%';
GRANT SELECT, SHOW VIEW, TRIGGER ON code_arena.* TO 'cca_backup'@'%';
GRANT USAGE ON *.* TO 'cca_health'@'127.0.0.1';
EOSQL

unset MYSQL_PWD app_password migration_password backup_password health_password value
