-- 初期スキーマ。設計は docs/design/data-model.md を参照。
-- テナントのデータを持つ表は主キーを (tenant_id, id) とし、外部キーも tenant_id を含む複合キーで張る。
-- これにより、テナントをまたぐ参照を DB が拒否する。

CREATE TABLE tenants (
  id                 uuid PRIMARY KEY DEFAULT uuidv7(),
  slug               text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$'),
  name               text NOT NULL,
  requester_signup   text NOT NULL CHECK (requester_signup IN ('open', 'domain_restricted')),
  allowed_domains    text[] NOT NULL DEFAULT '{}',
  next_ticket_number bigint NOT NULL DEFAULT 1,
  created_at         timestamptz NOT NULL
);

CREATE TABLE organizations (
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  id        uuid NOT NULL DEFAULT uuidv7(),
  name      text NOT NULL,
  PRIMARY KEY (tenant_id, id),
  UNIQUE (tenant_id, name)
);

CREATE TABLE users (
  tenant_id         uuid NOT NULL REFERENCES tenants(id),
  id                uuid NOT NULL DEFAULT uuidv7(),
  email             text NOT NULL,
  name              text NOT NULL,
  role              text NOT NULL CHECK (role IN ('requester', 'agent', 'admin')),
  organization_id   uuid,
  password_hash     text,                  -- 招待を受諾するまで NULL
  email_verified_at timestamptz,
  active            boolean NOT NULL DEFAULT true,
  created_at        timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, id),
  UNIQUE (tenant_id, email),               -- F-X-4: 別テナントなら同じアドレスを使える
  FOREIGN KEY (tenant_id, organization_id) REFERENCES organizations(tenant_id, id)
);

CREATE TABLE agent_groups (                -- group は予約語のため agent_groups とする
  tenant_id uuid NOT NULL REFERENCES tenants(id),
  id        uuid NOT NULL DEFAULT uuidv7(),
  name      text NOT NULL,
  PRIMARY KEY (tenant_id, id),
  UNIQUE (tenant_id, name)
);

CREATE TABLE group_memberships (
  tenant_id uuid NOT NULL,
  group_id  uuid NOT NULL,
  user_id   uuid NOT NULL,
  PRIMARY KEY (tenant_id, group_id, user_id),
  FOREIGN KEY (tenant_id, group_id) REFERENCES agent_groups(tenant_id, id) ON DELETE CASCADE,
  FOREIGN KEY (tenant_id, user_id)  REFERENCES users(tenant_id, id)
);

CREATE TABLE tickets (
  tenant_id    uuid NOT NULL REFERENCES tenants(id),
  id           uuid NOT NULL DEFAULT uuidv7(),
  number       bigint NOT NULL,
  subject      text NOT NULL CHECK (length(subject) BETWEEN 1 AND 250),
  requester_id uuid NOT NULL,
  status       text NOT NULL CHECK (status IN ('new', 'open', 'pending', 'on_hold', 'solved', 'closed')),
  priority     text NOT NULL CHECK (priority IN ('low', 'normal', 'high', 'urgent')),
  type         text NOT NULL CHECK (type IN ('question', 'incident', 'problem', 'task')),
  group_id     uuid,
  assignee_id  uuid,
  tags         text[] NOT NULL DEFAULT '{}',
  follow_up_of uuid,
  version      int NOT NULL DEFAULT 0,
  created_at   timestamptz NOT NULL,
  updated_at   timestamptz NOT NULL,
  solved_at    timestamptz,
  closed_at    timestamptz,
  PRIMARY KEY (tenant_id, id),
  UNIQUE (tenant_id, number),
  FOREIGN KEY (tenant_id, requester_id) REFERENCES users(tenant_id, id),
  FOREIGN KEY (tenant_id, group_id)     REFERENCES agent_groups(tenant_id, id),
  -- F-T-6: 担当者は担当グループに所属していること。assignee_id が NULL なら検査されない（MATCH SIMPLE）。
  -- 所属を外したら担当者だけを外す。
  FOREIGN KEY (tenant_id, group_id, assignee_id)
    REFERENCES group_memberships(tenant_id, group_id, user_id) ON DELETE SET NULL (assignee_id),
  FOREIGN KEY (tenant_id, follow_up_of) REFERENCES tickets(tenant_id, id),
  -- MATCH SIMPLE は列のどれかが NULL だと検査しないため、担当者がいるならグループも必須にする
  CHECK (assignee_id IS NULL OR group_id IS NOT NULL),
  -- 要件 7 のうち、行単体で判定できる規則
  CHECK (status <> 'new' OR assignee_id IS NULL),
  CHECK (status NOT IN ('solved', 'closed') OR assignee_id IS NOT NULL),
  CHECK ((status IN ('solved', 'closed')) = (solved_at IS NOT NULL)),
  CHECK ((status = 'closed') = (closed_at IS NOT NULL))
);

CREATE TABLE comments (
  tenant_id  uuid NOT NULL,
  id         uuid NOT NULL DEFAULT uuidv7(),
  ticket_id  uuid NOT NULL,
  author_id  uuid NOT NULL,
  body       text NOT NULL CHECK (length(body) BETWEEN 1 AND 65536),
  public     boolean NOT NULL,
  created_at timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, ticket_id) REFERENCES tickets(tenant_id, id),
  FOREIGN KEY (tenant_id, author_id) REFERENCES users(tenant_id, id)
);

CREATE TABLE attachments (
  tenant_id    uuid NOT NULL,
  id           uuid NOT NULL DEFAULT uuidv7(),
  comment_id   uuid,                        -- コメントに紐付くまで NULL
  uploader_id  uuid NOT NULL,
  filename     text NOT NULL,
  content_type text NOT NULL,
  size         bigint NOT NULL CHECK (size BETWEEN 1 AND 20971520),
  storage_key  text NOT NULL UNIQUE,
  created_at   timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, comment_id)  REFERENCES comments(tenant_id, id),
  FOREIGN KEY (tenant_id, uploader_id) REFERENCES users(tenant_id, id)
);

CREATE TABLE ticket_events (
  tenant_id  uuid NOT NULL,
  id         uuid NOT NULL DEFAULT uuidv7(),
  ticket_id  uuid NOT NULL,
  change_id  uuid NOT NULL,                 -- 1 回の更新で変わった項目をまとめる
  actor_id   uuid,                          -- NULL はシステム（自動クローズなど）
  field      text NOT NULL,
  from_value jsonb,
  to_value   jsonb,
  created_at timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, ticket_id) REFERENCES tickets(tenant_id, id),
  FOREIGN KEY (tenant_id, actor_id)  REFERENCES users(tenant_id, id)
);

CREATE TABLE views (
  tenant_id  uuid NOT NULL,
  id         uuid NOT NULL DEFAULT uuidv7(),
  owner_id   uuid,                          -- NULL なら共有ビュー（管理者が作成）
  name       text NOT NULL,
  conditions jsonb NOT NULL,
  sort       jsonb NOT NULL,
  position   int NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, owner_id) REFERENCES users(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE user_tokens (
  tenant_id  uuid NOT NULL,
  id         uuid NOT NULL DEFAULT uuidv7(),
  user_id    uuid NOT NULL,
  purpose    text NOT NULL CHECK (purpose IN ('invitation', 'email_verification', 'password_reset')),
  token_hash bytea NOT NULL UNIQUE,         -- SHA-256。生の値はメールにだけ載る
  expires_at timestamptz NOT NULL,
  used_at    timestamptz,
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id)
);

CREATE TABLE notification_outbox (
  tenant_id       uuid NOT NULL,
  id              uuid NOT NULL DEFAULT uuidv7(),
  kind            text NOT NULL,
  recipient_id    uuid NOT NULL,
  payload         jsonb NOT NULL,
  state           text NOT NULL DEFAULT 'pending' CHECK (state IN ('pending', 'sent', 'failed')),
  attempts        int NOT NULL DEFAULT 0,
  next_attempt_at timestamptz NOT NULL,
  sent_at         timestamptz,
  created_at      timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, recipient_id) REFERENCES users(tenant_id, id)
);

-- 一覧・標準ビュー
CREATE INDEX tickets_status_updated  ON tickets (tenant_id, status, updated_at DESC);
CREATE INDEX tickets_assignee_status ON tickets (tenant_id, assignee_id, status);
CREATE INDEX tickets_group_status    ON tickets (tenant_id, group_id, status);
CREATE INDEX tickets_requester       ON tickets (tenant_id, requester_id, updated_at DESC);
CREATE INDEX tickets_updated         ON tickets (tenant_id, updated_at DESC);
CREATE INDEX tickets_tags            ON tickets USING gin (tags);
-- 自動クローズの対象検索
CREATE INDEX tickets_solved_at       ON tickets (solved_at) WHERE status = 'solved';

-- 詳細画面
CREATE INDEX comments_ticket         ON comments (tenant_id, ticket_id, created_at);
CREATE INDEX ticket_events_ticket    ON ticket_events (tenant_id, ticket_id, created_at);
CREATE INDEX attachments_comment     ON attachments (tenant_id, comment_id);

-- 全文検索（pg_bigm。拡張は DB イメージの初期化で作成済み）
CREATE INDEX tickets_subject_bigm    ON tickets  USING gin (subject gin_bigm_ops);
CREATE INDEX comments_body_bigm      ON comments USING gin (body gin_bigm_ops);

-- 送信ジョブ
CREATE INDEX outbox_pending          ON notification_outbox (next_attempt_at) WHERE state = 'pending';

-- API プロセスの接続ロール。コメントと監査ログは追記のみ（F-C-2、F-L-2）なので UPDATE / DELETE を与えない。
-- 表を追加するマイグレーションでは、ここと同じように権限を与えること（MigrationIT が検査する）。
GRANT SELECT, INSERT, UPDATE, DELETE ON
  tenants, organizations, users, agent_groups, group_memberships, tickets,
  attachments, views, user_tokens, notification_outbox
  TO app_runtime;
GRANT SELECT, INSERT ON comments, ticket_events TO app_runtime;
