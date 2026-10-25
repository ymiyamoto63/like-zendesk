import createClient, { type Middleware } from 'openapi-fetch'
import type { components, paths } from './schema'

export type Me = components['schemas']['MeResponse']
export type Role = Me['role']

/** API のエラー応答（RFC 9457 の problem+json）。`code` に機械可読な理由が入る。 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly errors: { field: string; code: string }[] = [],
  ) {
    super(`${status} ${code}`)
  }
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/** 状態を変えるリクエストに CSRF トークンを付ける。Cookie がまだなければ先に発行してもらう。 */
const csrf: Middleware = {
  async onRequest({ request }) {
    if (request.method === 'GET') return request
    if (!readCookie('XSRF-TOKEN')) await fetch('/api/csrf', { credentials: 'same-origin' })
    request.headers.set('X-XSRF-TOKEN', decodeURIComponent(readCookie('XSRF-TOKEN') ?? ''))
    return request
  },
}

// 画面と API は同じオリジン（nginx または Vite の開発サーバーが /api を転送する）
export const api = createClient<paths>({
  baseUrl: location.origin,
  credentials: 'same-origin',
  // 呼び出しのたびに globalThis.fetch を引く（テストで差し替えられるように）
  fetch: (request) => globalThis.fetch(request),
})
api.use(csrf)

type Problem = { code?: string; errors?: { field: string; code: string }[] }

/** openapi-fetch の結果から値を取り出す。エラー応答は ApiError にして投げる。 */
export async function unwrap<T>(
  call: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await call
  if (response.ok) return data as T
  const problem = (error ?? {}) as Problem
  throw new ApiError(response.status, problem.code ?? 'unknown', problem.errors ?? [])
}
