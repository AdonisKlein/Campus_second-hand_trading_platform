#!/bin/sh
set -eu

mode="$1"
database="$2"
sql_file="$3"

case "$mode" in
  import)
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -D "$database" < "$sql_file"
    ;;
  validate)
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B -D "$database" < "$sql_file"
    ;;
  *)
    echo "Unsupported MySQL client mode: $mode" >&2
    exit 64
    ;;
esac
