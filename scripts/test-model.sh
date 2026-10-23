#!/bin/bash
# Uses a disposable database; never migrates the working medical_materials database.
source "$(dirname "$0")/common.sh"
export PGPASSWORD="$DB_PASSWORD"
TEST_DATABASE="vkr_model_test_$(date +%s)_$$"
"$PG_BIN/createdb" -h 127.0.0.1 -p 5433 -U "$DB_USER" "$TEST_DATABASE"
cleanup() { "$PG_BIN/dropdb" -h 127.0.0.1 -p 5433 -U "$DB_USER" --if-exists "$TEST_DATABASE"; }
trap cleanup EXIT
"$PG_BIN/psql" -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5433 -U "$DB_USER" -d "$TEST_DATABASE" -f src/main/resources/db/migration/V1__create_materials.sql
"$PG_BIN/psql" -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5433 -U "$DB_USER" -d "$TEST_DATABASE" -c "INSERT INTO materials (id, code, name, unit) VALUES (501, 'LEGACY-MIGRATION-CHECK', 'Тестовый материал до миграции', 'упак')"
export DB_URL="jdbc:postgresql://127.0.0.1:5433/$TEST_DATABASE"
# Only the disposable fixture is baselined at V1; production keeps its original Flyway history.
export SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
export SPRING_FLYWAY_BASELINE_VERSION=1
./mvnw -B -Pintegration-tests verify
