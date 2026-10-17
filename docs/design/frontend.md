# フロントエンド設計

- 版: v0.1（2026-10-17）
- 前提: [要件定義書](../requirements.md)、[API 設計](api.md)

## 1. 方針

- エージェント画面、依頼者ポータル、サインアップ・ログインを 1 つの Vue 3 SPA にまとめる。認証、API クライアント、Markdown 表示などの部品を共有でき、ビルドと配信が 1 つで済む。
- 画面の領域ごとに遅延読み込み（route-level code splitting）し、依頼者がエージェント画面のコードを読み込まないようにする。
- エージェント画面は PC 向け、ポータルはスマートフォンにも対応するレスポンシブ表示とする（N-6）。

### 1.1 採用ライブラリ

| 用途 | ライブラリ | 理由 |
| --- | --- | --- |
| 骨格 | Vue 3（Composition API、`<script setup>`）、TypeScript、Vite | 要件 8.1 |
| ルーティング | Vue Router | 要件 8.1 |
| クライアントの状態 | Pinia | ログイン中のユーザー、UI の状態だけを持つ |
| サーバーの状態 | TanStack Query（Vue） | 取得結果のキャッシュ、更新後の再取得、読み込み中・エラー状態を自前で書かずに済む |
| UI 部品 | PrimeVue（Aura テーマ） | 並べ替え・ページング付きの表、日付選択、複数選択など、エージェント画面に要る部品が揃っている |
| API クライアント | openapi-fetch ＋ openapi-typescript | OpenAPI から型を生成し、API の変更を型エラーで検出する |
| Markdown | markdown-it（HTML 無効）＋ DOMPurify | 表示時にサニタイズする（F-C-4、N-5） |
| テスト | Vitest、Vue Test Utils | 要件 8.1 |

## 2. 画面と URL

```
/signup                                  テナントの作成（F-X-1）
/t/:slug/login                           ログイン（ロールに応じて遷移先を変える）
/t/:slug/invitations/:token              招待の受諾
/t/:slug/verify-email/:token             メールアドレスの確認
/t/:slug/password-reset/:token           パスワード再設定

# 依頼者ポータル
/t/:slug                                 ポータルのトップ（起票への導線、自分のチケット）
/t/:slug/register                        依頼者の自己登録
/t/:slug/requests                        自分のチケット一覧
/t/:slug/requests/new                    起票
/t/:slug/requests/:number                詳細・返信

# エージェント画面
/t/:slug/agent                           → 既定のビューへリダイレクト
/t/:slug/agent/views/:viewId             ビューのチケット一覧
/t/:slug/agent/search?q=...              全文検索
/t/:slug/agent/tickets/new               チケット作成
/t/:slug/agent/tickets/:number           チケット詳細
/t/:slug/agent/views/new                 ビューの作成・編集

# 管理（管理者のみ）
/t/:slug/agent/admin/settings            テナント設定
/t/:slug/agent/admin/users               ユーザー・招待
/t/:slug/agent/admin/groups              グループ
/t/:slug/agent/admin/organizations       所属組織
```

- 画面の URL のチケット番号は、API のパスと同じ番号を使う。
- ルートの `meta.requires`（`'public' | 'requester' | 'staff' | 'admin'`）をナビゲーションガードで検査し、未ログインならログイン画面へ、ロールが合わなければ 404 画面へ遷移する。最終的な権限の判定は API が行い、ガードは表示の切り替えのためだけにある。

## 3. 主な画面

### 3.1 エージェント: チケット一覧

```
┌───────────────┬───────────────────────────────────────────────┐
│ ビュー        │  未割り当てチケット (12)          [一括更新]  │
│ ─ 標準        │ ┌──┬────┬──────────────┬──────┬──────┬─────┐ │
│  自分の未解決 8│ │□ │ #  │ 件名         │状態  │優先度│更新 │ │
│  未割り当て 12│ ├──┼────┼──────────────┼──────┼──────┼─────┤ │
│  自グループ 30│ │□ │123 │VPN に接続… │新規  │高    │5分前│ │
│  最近更新     │ └──┴────┴──────────────┴──────┴──────┴─────┘ │
│ ─ 共有        │                         < 1 2 3 … >           │
│ ─ 個人        │                                               │
│  [+ ビュー]   │                                               │
└───────────────┴───────────────────────────────────────────────┘
```

- 並べ替えとページは URL のクエリに入れ、再読み込みやリンク共有で同じ一覧を表示できるようにする。
- 行を選ぶと一括更新（F-T-8）のパネルを出す。

### 3.2 エージェント: チケット詳細

```
┌──────────────────────────────────────────┬──────────────────┐
│ #123 VPN に接続できない                  │ 依頼者 山田 花子 │
│                                          │ 状態   [オープン▼]│
│ ── 山田 花子（公開）10:00 ───────────── │ 優先度 [高     ▼]│
│ 本文 …                                   │ 種別   [インシ ▼]│
│ ── 佐藤 一郎（社内メモ）10:30 ───────── │ グループ[インフラ▼]│
│ （背景色を変えて区別）                   │ 担当者 [佐藤   ▼]│
│ ── 状態: 新規 → オープン（佐藤）──────── │ タグ   vpn ×     │
│                                          │                  │
│ ┌ [公開返信] [社内メモ] ───────────────┐ │                  │
│ │                                      │ │                  │
│ └──────────── [送信して: 保留 ▼] ──────┘ │                  │
└──────────────────────────────────────────┴──────────────────┘
```

- コメントと監査ログを 1 本の時系列に並べる（F-L-1）。
- 社内メモは背景色とラベルで公開コメントと明確に区別する（F-C-1）。入力欄も、社内メモのタブを選ぶと色が変わる。
- 送信ボタンでステータスを同時に変えられる（F-C-3）。右側の項目を変えてから送信すると、コメントと項目の変更を 1 回の API 呼び出しで送る。
- 担当者の選択肢は、選んだグループの所属メンバーだけにする（F-T-6）。
- 409（版の競合）を受け取ったら、最新の内容を再取得し、入力中のコメントを残したまま「他の人が更新しました」と表示する。

### 3.3 依頼者ポータル

- 自分のチケット一覧、起票フォーム、詳細（公開コメントのみ）、返信フォームの 4 画面。
- スマートフォンでは 1 列で表示する。
- 返信したチケットが保留・解決済みならオープンに戻る旨を、送信後の画面で表示する。

## 4. ディレクトリ構成

```
frontend/src/
├── main.ts
├── router/
│   ├── index.ts            # 領域ごとのルート定義を束ねる
│   └── guards.ts           # meta.requires の検査
├── api/
│   ├── openapi.json        # バックエンドから生成（コミットする）
│   ├── schema.d.ts         # openapi-typescript の生成物
│   └── client.ts           # openapi-fetch のクライアント、CSRF ヘッダー付与、エラー変換
├── session/
│   └── store.ts            # Pinia: 現在のテナントとログインユーザー（GET /me）
├── features/
│   ├── auth/               # サインアップ、ログイン、招待、メール確認
│   ├── tickets/            # 一覧、詳細、作成、一括更新、コメント入力
│   ├── views/              # ビューのサイドバー、条件エディター
│   ├── search/
│   ├── portal/             # 依頼者ポータルの画面
│   └── admin/              # 管理画面
├── shared/
│   ├── components/         # MarkdownView、UserPicker、StatusBadge など
│   ├── composables/
│   └── labels.ts           # ステータス・優先度・種別の日本語表示名
└── layouts/
    ├── AgentLayout.vue
    ├── PortalLayout.vue
    └── PublicLayout.vue
```

- 各 `features/*` は `routes.ts`、`pages/`、`components/`、`queries.ts`（TanStack Query のクエリとミューテーション）を持つ。
- サーバーから取得したデータは Pinia に複製せず、TanStack Query のキャッシュだけに置く。データの持ち主を 1 つにするため。

## 5. API 呼び出し

```ts
// api/client.ts
import createClient from 'openapi-fetch';
import type { paths } from './schema';

export const api = createClient<paths>({ baseUrl: '/' });
api.use({
  onRequest({ request }) {
    if (request.method !== 'GET') request.headers.set('X-XSRF-TOKEN', readCookie('XSRF-TOKEN'));
    return request;
  },
});

// features/tickets/queries.ts
export function useTicket(slug: Ref<string>, number: Ref<number>) {
  return useQuery({
    queryKey: ['tickets', slug, number],
    queryFn: () => unwrap(api.GET('/api/t/{slug}/tickets/{number}', { params: { path: { slug: slug.value, number: number.value } } })),
  });
}
```

- `unwrap` は problem+json を `ApiError`（`status`、`code`、`errors`）に変換して投げる。401 はログイン画面へ、404 は 404 画面へ、共通の処理で遷移する。
- 更新系のミューテーションは成功後に関係するクエリ（チケット詳細、コメント、ビューの件数）を無効化する。

## 6. テスト

- Vitest: 表示名の変換、ビュー条件エディター、コメント入力（公開／社内メモの切り替え、送信内容）、Markdown のサニタイズ。
- Playwright（`e2e/`）: 受け入れ基準 1〜13 を 1 シナリオずつ。メールの到達は Mailpit の API で確かめる。
