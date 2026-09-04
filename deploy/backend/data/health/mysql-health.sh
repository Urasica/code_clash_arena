#!/bin/sh
set -eu

result=$(sh /usr/local/bin/cca-mysql-client mysql health --batch --skip-column-names --execute 'SELECT 1')
[ "$result" = "1" ]
