#!/bin/bash
source "$(dirname "$0")/common.sh"
mkdir -p .local
if [ ! -f .local/postgres/PG_VERSION ]; then
    printf '%s\n' "$DB_PASSWORD" > .local/pg-password
    chmod 600 .local/pg-password
    "$PG_BIN/initdb" -D .local/postgres -U "$DB_USER" --pwfile=.local/pg-password --auth=scram-sha-256 --encoding=UTF8 --locale=C
    rm .local/pg-password
fi
if ! "$PG_BIN/pg_ctl" -D .local/postgres status >/dev/null 2>&1; then
    "$PG_BIN/pg_ctl" -D .local/postgres -l .local/postgres.log -o "-p 5433 -h 127.0.0.1 -k '$ROOT/.local'" -w start
fi
export PGPASSWORD="$DB_PASSWORD"
if ! "$PG_BIN/psql" -h 127.0.0.1 -p 5433 -U "$DB_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='medical_materials'" | grep -q 1; then
    "$PG_BIN/createdb" -h 127.0.0.1 -p 5433 -U "$DB_USER" medical_materials
fi
"$PG_BIN/pg_isready" -h 127.0.0.1 -p 5433
