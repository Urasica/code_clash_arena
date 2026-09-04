#!/bin/sh
set -eu

runtime_dir=/tmp/cca-redis-secrets
umask 077
mkdir -p "$runtime_dir"
cp /run/secrets/redis-acl "$runtime_dir/redis.acl"
cp /run/secrets/redis-app-password "$runtime_dir/redis-app-password"
chmod 750 "$runtime_dir"
chmod 440 "$runtime_dir/redis.acl" "$runtime_dir/redis-app-password"
chown redis:root "$runtime_dir" "$runtime_dir/redis.acl" "$runtime_dir/redis-app-password"

exec /usr/local/bin/docker-entrypoint.sh "$@"
