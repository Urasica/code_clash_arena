#!/bin/sh
set -eu
REDISCLI_AUTH=$(cat /run/secrets/redis-app-password)
export REDISCLI_AUTH
exec redis-cli --no-auth-warning --user cca_app "$@"
