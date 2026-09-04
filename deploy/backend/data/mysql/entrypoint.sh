#!/bin/sh
set -eu
umask 077
cp /opt/cca/cca.cnf /tmp/cca.cnf
chmod 600 /tmp/cca.cnf
chown mysql:mysql /tmp/cca.cnf
exec /usr/local/bin/docker-entrypoint.sh "$@"
