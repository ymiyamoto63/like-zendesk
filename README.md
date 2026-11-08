# like-zendesk

Zendesk ライクなインシデント管理システム。

## 概要

問い合わせやインシデントをチケットとして受け付け、担当者の割り当て、ステータス管理、コメントによるやり取りを行うためのシステムです。

## 主な機能（予定）

- チケットの作成・閲覧・更新
- ステータス管理（新規 / 対応中 / 保留 / 解決済み / クローズ）
- 優先度・担当者の設定
- コメント・社内メモ
- 検索・フィルタ

## 起動

Docker があれば、次の 1 コマンドで全体が起動します。

```sh
docker compose up --build
```

| URL | 内容 |
| --- | --- |
| http://localhost:8080 | 画面（`/api` は API に転送）。`/signup` でテナントを作成する |
| http://localhost:8080/api/actuator/health | API の死活 |
| http://localhost:8080/api/docs | OpenAPI 定義 |
| http://localhost:8025 | Mailpit（開発用のメール受信箱） |

## 開発

必要なもの: Java 25、Node.js 22、Docker。

```sh
docker compose up -d db mailpit           # DB とメールだけをコンテナで動かす
(cd backend && ./mvnw spring-boot:run)    # API: http://localhost:8081
(cd frontend && npm install && npm run dev)  # 画面: http://localhost:5173（/api は 8081 へ転送）
```

テスト:

```sh
(cd backend && ./mvnw verify)   # 整形の検査、単体・アーキテクチャテスト、結合テスト（Docker が必要）
(cd frontend && npm run lint && npm run typecheck && npm test)
```

API を変えたら、OpenAPI 定義とフロントエンドの型を生成し直してコミットします（CI がずれを検査します）。

```sh
(cd backend && ./mvnw verify -Dopenapi.update=true)   # frontend/src/api/openapi.json を書き直す
(cd frontend && npm run gen:api)                      # src/api/schema.d.ts を生成する
```

## 構成

| ディレクトリ | 内容 |
| --- | --- |
| `backend/` | Spring Boot 4（Java 25、Maven）。Flyway のマイグレーションは `src/main/resources/db/migration` |
| `frontend/` | Vue 3 + TypeScript（Vite） |
| `docker/` | PostgreSQL 18 + pg_bigm のイメージ、API と画面（nginx）のイメージ |
| `docs/` | 要件定義書、設計ドキュメント |

## ドキュメント

- [要件定義書](docs/requirements.md)
- [設計ドキュメント](docs/design/README.md)

## ステータス

開発初期段階です。[実装の順序](docs/design/README.md#3-次の段階)のうち、骨組み（段階 1）と、テナント・認証（段階 2）のうちサインアップとログインまで進んでいます。
