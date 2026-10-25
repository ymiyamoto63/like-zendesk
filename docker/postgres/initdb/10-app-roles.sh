#!/bin/bash
# マイグレーション用（app_owner）と API 実行用（app_runtime）のロールを作る。
# app_runtime の表ごとの権限は Flyway のマイグレーションで与える。
set -euo pipefail

: "${APP_OWNER_PASSWORD:?APP_OWNER_PASSWORD is required}"
: "${APP_RUNTIME_PASSWORD:?APP_RUNTIME_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v owner_password="$APP_OWNER_PASSWORD" -v runtime_password="$APP_RUNTIME_PASSWORD" <<'SQL'
CREATE ROLE app_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE app_runtime LOGIN PASSWORD :'runtime_password';
SELECT format('ALTER DATABASE %I OWNER TO app_owner', current_database()) \gexec
ALTER SCHEMA public OWNER TO app_owner;
GRANT USAGE ON SCHEMA public TO app_runtime;
-- 拡張の作成にはスーパーユーザー権限が要るため、マイグレーションではなくここで作る
CREATE EXTENSION pg_bigm;
SQL
