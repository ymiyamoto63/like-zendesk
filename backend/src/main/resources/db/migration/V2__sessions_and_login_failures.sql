-- Spring Session JDBC のセッション（docs/design/architecture.md 2.8）。
-- 表の定義は Spring Session の schema-postgresql.sql と同じ。
CREATE TABLE spring_session (
  primary_id            char(36) NOT NULL PRIMARY KEY,
  session_id            char(36) NOT NULL UNIQUE,
  creation_time         bigint NOT NULL,
  last_access_time      bigint NOT NULL,
  max_inactive_interval int NOT NULL,
  expiry_time           bigint NOT NULL,
  principal_name        varchar(100)
);
CREATE INDEX spring_session_expiry ON spring_session (expiry_time);
CREATE INDEX spring_session_principal ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
  session_primary_id char(36) NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE,
  attribute_name     varchar(200) NOT NULL,
  attribute_bytes    bytea NOT NULL,
  PRIMARY KEY (session_primary_id, attribute_name)
);

-- ログイン失敗の記録。テナント＋メールアドレス単位で試行回数を制限する（10 回／15 分）。
-- メールアドレスは存在しないものも記録する（存在の有無で挙動を変えない）ため、users への外部キーは張らない。
CREATE TABLE login_failures (
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  id        uuid NOT NULL DEFAULT uuidv7(),
  email     text NOT NULL,
  failed_at timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, id)
);
CREATE INDEX login_failures_email ON login_failures (tenant_id, email, failed_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON spring_session, spring_session_attributes, login_failures TO app_runtime;
