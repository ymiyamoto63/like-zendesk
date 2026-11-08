import { expect, type APIRequestContext, type Browser, type Page } from '@playwright/test'

const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025'

export const PASSWORD = 'correct-horse-battery'

/** テストごとに重ならない識別子。テナントのスラッグやメールアドレスに使う。 */
export function unique(prefix: string) {
  return `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`
}

interface MailSummary {
  ID: string
  Subject: string
}

/** {@code to} 宛てで件名に {@code subject} を含むメールが届くのを待ち、本文を返す。 */
export async function waitForMail(request: APIRequestContext, to: string, subject: string) {
  let found: MailSummary | undefined
  await expect
    .poll(
      async () => {
        const res = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `to:"${to}"` } })
        const { messages } = (await res.json()) as { messages: MailSummary[] }
        found = messages.find((m) => m.Subject.includes(subject))
        return found !== undefined
      },
      { message: `「${subject}」のメールが ${to} に届く`, timeout: 30_000 },
    )
    .toBe(true)
  const res = await request.get(`${MAILPIT}/api/v1/message/${found!.ID}`)
  const message = (await res.json()) as { Subject: string; Text: string }
  return message
}

/** メール本文の最初のリンクを、テスト対象のオリジンに合わせて返す。 */
export function linkIn(text: string, baseURL: string) {
  const url = new URL(text.match(/https?:\/\/\S+/)![0])
  return new URL(url.pathname + url.search, baseURL).toString()
}

/** サーバーの時計を進め、自動クローズなどの定期ジョブを実行させる（e2e プロファイルのみ）。 */
export async function advanceClock(page: Page, duration: string) {
  const res = await api(page, 'POST', '/api/_e2e/clock', { advance: duration })
  expect(res.status, 'POST /api/_e2e/clock').toBe(200)
  return res.body as { now: string; closed: number; mailed: number }
}

/** ページのセッション（Cookie と CSRF トークン）で API を呼ぶ。画面からはできない操作の確認に使う。 */
export async function api(page: Page, method: string, path: string, body?: unknown) {
  return page.evaluate(
    async ([method, path, body]) => {
      if (!document.cookie.includes('XSRF-TOKEN=')) await fetch('/api/csrf')
      const token = decodeURIComponent(
        document.cookie
          .split('; ')
          .find((c) => c.startsWith('XSRF-TOKEN='))!
          .slice('XSRF-TOKEN='.length),
      )
      const res = await fetch(path as string, {
        method: method as string,
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
      const text = await res.text()
      return { status: res.status, body: text ? JSON.parse(text) : null }
    },
    [method, path, body] as const,
  )
}

export async function newPage(browser: Browser) {
  const context = await browser.newContext()
  return context.newPage()
}

/** サインアップ画面でテナントを作る。管理者としてログインした状態になる。 */
export async function signUpTenant(page: Page, slug: string, name: string, admin: { name: string; email: string }) {
  await page.goto('/signup')
  await page.fill('#tenant-name', name)
  await page.fill('#slug', slug)
  await page.fill('#admin-name', admin.name)
  await page.fill('#email', admin.email)
  await page.fill('#password', PASSWORD)
  await page.click('button[type=submit]')
  await page.waitForURL(`**/t/${slug}/agent/**`)
}

/** ポータルで依頼者として自己登録し、確認メールのリンクを開いてログインする。 */
export async function registerRequester(page: Page, slug: string, person: { name: string; email: string }) {
  await page.goto(`/t/${slug}/register`)
  await page.fill('#name', person.name)
  await page.fill('#email', person.email)
  await page.fill('#password', PASSWORD)
  await page.click('button[type=submit]')
  await expect(page.getByText('確認メールを送りました')).toBeVisible()
  const mail = await waitForMail(page.request, person.email, 'メールアドレスの確認')
  await page.goto(linkIn(mail.Text, page.url()))
  await page.waitForURL(new RegExp(`/t/${slug}$`))
}
