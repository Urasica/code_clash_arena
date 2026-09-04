#!/bin/sh
result=$(sh /usr/local/bin/cca-redis-cli ping)
[ "$result" = "PONG" ]
