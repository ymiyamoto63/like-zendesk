# API 設計

- 版: v0.1（2026-10-17）
- 前提: [要件定義書](../requirements.md)、[アーキテクチャ設計](architecture.md)、[データモデル設計](data-model.md)

## 1. 共通規約

- ベースパス: `/api`。テナントのデータは `/api/t/{slug}/...` の下に置く。
- 形式: JSON（UTF-8）。項目名は camelCase。日時は ISO 8601（UTC、例 `2026-10-17T12:00:00Z`）。
- 認証: セッション Cookie（`SESSION`）。状態を変えるリクエスト（POST / PATCH / PUT / DELETE）には `X-XSRF-TOKEN` ヘッダーが必要。
- ID: チケットはテナント内の番号（`/tickets/123`）で指定する。画面の URL と揃えるため。他のリソースは UUID で指定する。
- エラー: RFC 9457 の `application/problem+json`。種類は [アーキテクチャ設計 2.11](architecture.md#211-エラー応答) を参照。
- 他テナントのリソースや見えないリソースは、存在しない場合と同じ 404 を返す。
- 仕様は springdoc-openapi で生成し、`/api/docs`（JSON）と `/api/swagger-ui` で公開する（F-D-3）。

### 1.1 一覧の取得

```
GET /api/t/{slug}/tickets?view=builtin:my-unsolved&sort=-updatedAt&page=0&size=25
```

```json
{
  "items": [ ... ],
  "page": 0,
  "size": 25,
  "total": 1234
}
```

- `page` は 0 から数える。`size` の上限は 100。`sort` は項目名をカンマで区切り（最大 3 つ）、先頭の `-` は降順を表す。省略するとビューの並び順。

### 1.2 エージェント用とポータル用の分離

依頼者向けの API は `/api/t/{slug}/portal/...` に分け、エージェント向けとは別の DTO を返す。

- 依頼者向けの DTO には、担当グループ、タグ、監査ログなどの内部項目を最初から含めない。項目の出し分けを条件分岐で行うと、項目の追加時に漏れやすいため。
- 権限の境界が URL で見え、OpenAPI 上でも別の操作として読める。
- エージェント用 API に依頼者がアクセスすると 404 を返す（`Viewer.Staff` を要求するため）。ポータル用 API はエージェントが使っても構わないが、画面からは使わない。
- 可視性条件はどちらの API でも同じリポジトリを通るため、ポータル用 API でも依頼者は自分のチケットしか見えない。

## 2. エンドポイント一覧

認可欄: **公開** = ログイン不要、**本人** = ログイン済みの全ロール、**依頼者**、**スタッフ** = エージェントと管理者、**管理者**。

### 2.1 テナント・認証

| メソッド | パス | 認可 | 内容 | 要件 |
| --- | --- | --- | --- | --- |
| POST | `/api/tenants` | 公開 | テナントと最初の管理者を作成し、管理者としてログインする | F-X-1 |
| GET | `/api/t/{slug}` | 公開 | テナント名、依頼者の登録方法（ログイン画面とポータルの表示用） | F-X-2 |
| GET | `/api/csrf` | 公開 | CSRF トークンの Cookie を発行する | N-3 |
| POST | `/api/t/{slug}/session` | 公開 | ログイン | N-3 |
| DELETE | `/api/t/{slug}/session` | 本人 | ログアウト（このテナントだけ） | F-X-2 |
| GET | `/api/t/{slug}/me` | 本人 | 自分のプロフィール、ロール、所属グループ | |
| POST | `/api/t/{slug}/requesters` | 公開 | 依頼者の自己登録。確認メールを送る | F-A-2 |
| POST | `/api/t/{slug}/email-verifications` | 公開 | `{token}` でメールアドレスを確認する | F-A-2 |
| GET | `/api/t/{slug}/invitations/{token}` | 公開 | 招待の内容（招待されたメールアドレス）を確認する | F-A-1 |
| POST | `/api/t/{slug}/invitations/{token}/accept` | 公開 | 名前とパスワードを設定して招待を受諾する | F-A-1 |
| POST | `/api/t/{slug}/password-resets` | 公開 | パスワード再設定メールを送る | |
| PUT | `/api/t/{slug}/password-resets/{token}` | 公開 | 新しいパスワードを設定する | |

### 2.2 チケット（スタッフ）

| メソッド | パス | 内容 | 要件 |
| --- | --- | --- | --- |
| GET | `/api/t/{slug}/tickets` | 一覧。`view`、`q`（全文検索）、`sort`、`page`、`size` | F-V-1〜4 |
| POST | `/api/t/{slug}/tickets` | 依頼者を指定して作成 | F-T-1 |
| GET | `/api/t/{slug}/tickets/{number}` | 詳細 | F-T-2 |
| PATCH | `/api/t/{slug}/tickets/{number}` | 項目の更新（`version` 必須） | F-T-5, F-T-6 |
| PATCH | `/api/t/{slug}/tickets` | 一括更新（`numbers` と変更内容） | F-T-8 |
| GET | `/api/t/{slug}/tickets/{number}/comments` | コメント一覧（公開・社内メモ） | F-C-1 |
| POST | `/api/t/{slug}/tickets/{number}/comments` | コメント追加。同時に項目を更新できる | F-C-1, F-C-3 |
| GET | `/api/t/{slug}/tickets/{number}/events` | 監査ログ | F-L-1 |
| POST | `/api/t/{slug}/attachments` | 添付のアップロード（multipart） | F-T-7 |
| GET | `/api/t/{slug}/attachments/{id}` | 添付のダウンロード | F-T-7 |
| GET | `/api/t/{slug}/users?role=requester&q=...` | 依頼者・エージェントの検索（チケット作成、担当者選択用） | F-T-1, F-T-6 |
| GET | `/api/t/{slug}/groups` | グループと所属メンバー | F-T-6 |

### 2.3 ビュー（スタッフ）

| メソッド | パス | 内容 | 要件 |
| --- | --- | --- | --- |
| GET | `/api/t/{slug}/views?withCounts=true` | 標準・共有・個人ビューの一覧と件数 | F-V-1, F-V-5 |
| POST | `/api/t/{slug}/views` | 個人ビューの作成。管理者は `shared: true` で共有ビューを作れる | F-V-2 |
| PATCH | `/api/t/{slug}/views/{id}` | 変更（自分のビュー、または管理者が共有ビュー） | F-V-2 |
| DELETE | `/api/t/{slug}/views/{id}` | 削除 | F-V-2 |

### 2.4 ポータル（依頼者）

| メソッド | パス | 内容 | 要件 |
| --- | --- | --- | --- |
| GET | `/api/t/{slug}/portal/tickets` | 自分のチケット一覧 | F-P-2 |
| POST | `/api/t/{slug}/portal/tickets` | 起票（件名、本文、添付 ID） | F-P-1 |
| GET | `/api/t/{slug}/portal/tickets/{number}` | 詳細 | F-P-2 |
| GET | `/api/t/{slug}/portal/tickets/{number}/comments` | 公開コメントのみ | F-P-2 |
| POST | `/api/t/{slug}/portal/tickets/{number}/comments` | 返信。保留・解決済みならオープンに戻る。クローズ済みなら新チケットを起票する | F-P-3 |
| POST | `/api/t/{slug}/portal/tickets/{number}/solve` | 解決済みにする | F-P-4 |

添付のアップロードとダウンロードは、2.2 と同じ `/attachments` を使う（可視性条件で依頼者にも使える）。

### 2.5 管理（管理者）

| メソッド | パス | 内容 | 要件 |
| --- | --- | --- | --- |
| GET / PATCH | `/api/t/{slug}/admin/settings` | テナント名、依頼者の登録方法、許可ドメイン | F-X-6, F-A-2 |
| GET | `/api/t/{slug}/admin/users` | ユーザー一覧（ロール、状態で絞り込み） | F-A-1 |
| POST | `/api/t/{slug}/admin/invitations` | エージェント・管理者を招待する | F-A-1 |
| PATCH | `/api/t/{slug}/admin/users/{id}` | ロール、所属組織、有効／無効の変更 | F-A-1, F-A-4 |
| POST / PATCH / DELETE | `/api/t/{slug}/admin/groups[/{id}]` | グループの作成・変更・削除 | F-A-3 |
| PUT | `/api/t/{slug}/admin/groups/{id}/members` | 所属メンバーの置き換え | F-A-3 |
| GET / POST / PATCH / DELETE | `/api/t/{slug}/admin/organizations[/{id}]` | 所属組織 | F-A-4 |

### 2.6 開発用（`e2e` プロファイルのみ）

| メソッド | パス | 内容 |
| --- | --- | --- |
| POST | `/api/_e2e/clock` | `{advance: "P4DT1H"}` でサーバーの時刻を進め、定期ジョブを即時実行する（受け入れ基準 8） |

## 3. 主なリクエストとレスポンス

### 3.1 チケット詳細（スタッフ）

```json
{
  "number": 123,
  "subject": "VPN に接続できない",
  "status": "open",
  "priority": "high",
  "type": "incident",
  "requester": { "id": "0192...", "name": "山田 花子", "email": "hanako@example.com", "organization": { "id": "...", "name": "経理部" } },
  "group": { "id": "...", "name": "インフラ" },
  "assignee": { "id": "...", "name": "佐藤 一郎" },
  "tags": ["vpn"],
  "followUpOf": null,
  "version": 4,
  "createdAt": "2026-10-17T03:00:00Z",
  "updatedAt": "2026-10-17T04:12:00Z",
  "solvedAt": null,
  "closedAt": null
}
```

ポータル用の詳細は、このうち `number`、`subject`、`status`、`createdAt`、`updatedAt` と担当者の表示名だけを返す。

### 3.2 チケットの更新

```
PATCH /api/t/{slug}/tickets/123
```

```json
{
  "version": 4,
  "status": "pending",
  "assigneeId": "0192...",
  "groupId": "0192...",
  "tags": ["vpn", "remote"]
}
```

- 送った項目だけを変更する。`null` を送ると値を外す（担当者を外すなど）。項目を省略した場合と区別する。
- 応答は更新後のチケット詳細。`version` が一致しなければ 409 と最新の `version` を返す。
- 遷移違反は 422 で、`code` に理由を入れる（例: `status.solved_requires_assignee`）。

### 3.3 コメントの追加（項目の更新を伴う）

```
POST /api/t/{slug}/tickets/123/comments
```

```json
{
  "body": "ログを添付してください。",
  "public": true,
  "attachmentIds": ["0192..."],
  "ticket": { "version": 4, "status": "pending" }
}
```

- `ticket` を付けると、コメントの追加と項目の更新を 1 トランザクションで行う（F-C-3）。監査ログでは同じ `changeId` にまとまる。
- 応答は追加したコメントと更新後のチケット詳細。

### 3.4 監査ログ

```json
{
  "items": [
    {
      "changeId": "0192...",
      "actor": { "id": "...", "name": "佐藤 一郎" },
      "createdAt": "2026-10-17T04:12:00Z",
      "changes": [
        { "field": "status",  "from": "new", "to": "open" },
        { "field": "assignee", "from": null, "to": { "id": "...", "name": "佐藤 一郎" } }
      ]
    }
  ]
}
```

- `actor` が `null` のときはシステム（自動クローズ）による変更。
- 監査ログは `GET` だけを提供する（F-L-2）。

### 3.5 ビュー

```json
{
  "id": "builtin:unassigned",
  "name": "未割り当てチケット",
  "kind": "builtin",
  "conditions": { "all": [ { "field": "assignee", "op": "is_null" }, { "field": "status", "op": "in", "value": ["new", "open", "pending", "on_hold"] } ] },
  "sort": [ { "field": "createdAt", "direction": "asc" } ],
  "count": 12
}
```

- `kind` は `builtin`（標準）、`shared`（共有）、`personal`（個人）。
- 標準ビュー: `builtin:my-unsolved`（自分の未解決）、`builtin:unassigned`（未割り当て）、`builtin:my-groups-unsolved`（自グループの未解決）、`builtin:recently-updated`（最近更新）。

## 4. OpenAPI とフロントエンドの型

- バックエンドのテスト中に `/api/docs` の内容を `frontend/src/api/openapi.json` に書き出す Maven の設定（`verify` フェーズで springdoc-openapi-maven-plugin を実行）を用意し、ファイルをコミットする。
- フロントエンドは `openapi-typescript` でこのファイルから型を生成し、`openapi-fetch` で呼び出す。
- CI で、生成した `openapi.json` とコミット済みのファイルの差分がないことを確かめる。API を変えたのにフロントエンドの型が古いままになることを防ぐ。
- テナント分離の結合テスト（[アーキテクチャ設計 2.3](architecture.md#23-可視性条件)）も、この OpenAPI 定義からエンドポイントの一覧を取り出す。
