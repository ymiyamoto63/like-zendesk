import { expect, test } from '@playwright/test'
import {
  PASSWORD,
  advanceClock,
  api,
  linkIn,
  newPage,
  registerRequester,
  signUpTenant,
  unique,
  waitForMail,
} from './support'

// 要件定義 9 章の受け入れ基準 1〜13 を、画面の操作で順に通す。
// 前の基準で作ったテナント・ユーザー・チケットを次の基準で使うので、1 つのテストにまとめている。
test('受け入れ基準 1〜13', async ({ browser }) => {
  const slug = unique('a')
  const subject = `VPN に接続できない ${slug}`
  const admin = { name: '伊藤 直樹', email: `ito@${slug}.example.com` }
  const agent = { name: '渡辺 さくら', email: `watanabe@${slug}.example.com` }
  const requester = { name: '山田 花子', email: `hanako@${slug}.example.net` }
  const other = { name: '田中 一樹', email: `tanaka@${slug}.example.net` }
  let number = 0

  const adminPage = await newPage(browser)
  const agentPage = await newPage(browser)
  const requesterPage = await newPage(browser)

  await test.step('1. サインアップでテナント A を作り、管理者としてログインする', async () => {
    await signUpTenant(adminPage, slug, 'テナント A', admin)
    await expect(adminPage.getByText(`${admin.name}（管理者）`)).toBeVisible()
  })

  await test.step('2. 管理者がエージェントを招待し、グループに所属させる', async () => {
    await adminPage.getByRole('link', { name: '管理' }).click()
    await adminPage.fill('input[aria-label="招待するメールアドレス"]', agent.email)
    await adminPage.getByRole('button', { name: '招待する' }).click()
    await expect(adminPage.getByText('招待メールを送りました')).toBeVisible()

    const mail = await waitForMail(adminPage.request, agent.email, '招待が届いています')
    await agentPage.goto(linkIn(mail.Text, adminPage.url()))
    await agentPage.fill('#name', agent.name)
    await agentPage.fill('#password', PASSWORD)
    await agentPage.click('button[type=submit]')
    await agentPage.waitForURL(`**/t/${slug}/agent/**`)
    await expect(agentPage.getByText(`${agent.name}（エージェント）`)).toBeVisible()

    await adminPage.locator('nav[aria-label=管理メニュー]').getByText('グループ').click()
    await adminPage.fill('input[aria-label="新しいグループの名前"]', 'インフラ')
    await adminPage.getByRole('button', { name: '追加する' }).click()
    const group = adminPage.getByTestId('group-インフラ')
    await group.locator('.p-multiselect').click()
    await adminPage.locator(`li[aria-label^="${agent.name}"]`).click()
    await adminPage.keyboard.press('Escape')
    await adminPage.click('button[aria-label="インフラ を保存"]')
    await expect(adminPage.getByText('グループ「インフラ」を保存しました')).toBeVisible()
  })

  await test.step('3. 依頼者がポータルで自己登録して起票し、受付メールが届く', async () => {
    await registerRequester(requesterPage, slug, requester)
    await requesterPage.getByText('新しいお問い合わせ').first().click()
    await requesterPage.fill('#subject', subject)
    await requesterPage.fill('#body', '朝から VPN に接続できません。エラー 809 が表示されます。')
    await requesterPage.getByRole('button', { name: '送信する' }).click()
    await expect(requesterPage.getByText('お問い合わせを受け付けました')).toBeVisible()
    number = Number(requesterPage.url().match(/requests\/(\d+)/)![1])
    await waitForMail(requesterPage.request, requester.email, 'お問い合わせを受け付けました')
  })

  await test.step('4. エージェントが「未割り当て」ビューで見つけ、自分を担当者にするとオープンになる', async () => {
    // グループはエージェントのログイン後に作ったので、読み込み直す
    await agentPage.reload()
    await agentPage.getByRole('link', { name: '未割り当てチケット' }).click()
    await agentPage.getByRole('link', { name: subject }).click()
    await agentPage.click('#field-group')
    await agentPage.click('li[aria-label="インフラ"]')
    await agentPage.click('#field-assignee')
    await agentPage.click(`li[aria-label="${agent.name}"]`)
    await agentPage.getByRole('button', { name: '項目を更新' }).click()
    await expect(agentPage.locator('.detail header').getByText('オープン')).toBeVisible()
  })

  await test.step('5. 社内メモは依頼者のポータルに表示されない', async () => {
    await agentPage.getByText('社内メモ', { exact: true }).first().click()
    await agentPage.fill('#comment-body', 'VPN 装置のログを確認する（社内メモ）')
    await agentPage.getByRole('button', { name: 'メモを追加' }).click()
    await expect(agentPage.locator('article.internal')).toContainText('VPN 装置のログを確認する')

    await requesterPage.goto(`/t/${slug}/requests/${number}`)
    await expect(requesterPage.getByText('朝から VPN に接続できません')).toBeVisible()
    await expect(requesterPage.getByText('VPN 装置のログを確認する')).toHaveCount(0)
  })

  await test.step('6. 公開コメントで質問して保留にすると、依頼者にメールが届く', async () => {
    await agentPage.getByText('公開返信', { exact: true }).first().click()
    await agentPage.fill('#comment-body', 'お使いの PC の OS を教えてください。')
    await agentPage.click('#comment-status')
    await agentPage.click('li[aria-label="保留"]')
    await agentPage.getByRole('button', { name: '返信を送信' }).click()
    await expect(agentPage.locator('.detail header').getByText('保留')).toBeVisible()
    await waitForMail(agentPage.request, requester.email, '返信がありました')
  })

  await test.step('7. 依頼者が返信するとオープンに戻り、担当者にメールが届く', async () => {
    await requesterPage.reload()
    await requesterPage.fill('#reply-body', 'Windows 11 です。')
    await requesterPage.getByRole('button', { name: '返信を送信' }).click()
    await expect(requesterPage.getByText('「対応中」に戻りました')).toBeVisible()
    await agentPage.reload()
    await expect(agentPage.locator('.detail header').getByText('オープン')).toBeVisible()
    await waitForMail(agentPage.request, agent.email, '返信がありました')
  })

  await test.step('8. 解決済みにして 4 日たつとクローズになり、項目を変更できない', async () => {
    await agentPage.click('#field-status')
    await agentPage.click('li[aria-label="解決済み"]')
    await agentPage.getByRole('button', { name: '項目を更新' }).click()
    await expect(agentPage.locator('.detail header').getByText('解決済み')).toBeVisible()

    const clock = await advanceClock(agentPage, 'P4DT1H')
    expect(clock.closed).toBeGreaterThanOrEqual(1)
    await agentPage.reload()
    await expect(agentPage.locator('.detail header').getByText('クローズ')).toBeVisible()
    await expect(agentPage.locator('#field-status')).toHaveText('クローズ')
    await expect(agentPage.locator('#field-status')).toHaveAttribute('aria-disabled', 'true')
    await expect(agentPage.getByRole('button', { name: '項目を更新' })).toBeDisabled()
    const { body } = await api(agentPage, 'GET', `/api/t/${slug}/tickets/${number}`)
    const change = await api(agentPage, 'PATCH', `/api/t/${slug}/tickets/${number}`, {
      version: body.version,
      priority: 'high',
    })
    expect(change.status).toBe(422)
  })

  await test.step('9. 4〜8 の変更が監査ログとして時系列で表示される', async () => {
    const timeline = agentPage.locator('.timeline')
    const expected = [
      '状態: 新規 → オープン',
      '状態: オープン → 保留',
      '状態: 保留 → オープン',
      '状態: オープン → 解決済み',
      '状態: 解決済み → クローズ',
    ]
    for (const text of expected) await expect(timeline).toContainText(text)
    const all = await timeline.innerText()
    const positions = expected.map((text) => all.indexOf(text))
    expect(positions).toEqual([...positions].sort((a, b) => a - b))
    await expect(timeline).toContainText('担当者')
    await expect(timeline).toContainText('VPN 装置のログを確認する')
  })

  await test.step('10. 同じテナントの別の依頼者は、URL 直打ちでも API でも閲覧できない', async () => {
    const otherPage = await newPage(browser)
    await registerRequester(otherPage, slug, other)
    await otherPage.goto(`/t/${slug}/requests/${number}`)
    await expect(otherPage.getByText('このお問い合わせは見つかりません')).toBeVisible()
    await expect(otherPage.getByText(subject)).toHaveCount(0)
    for (const path of [`/api/t/${slug}/portal/tickets/${number}`, `/api/t/${slug}/portal/tickets/${number}/comments`]) {
      // 起票した本人には見えるパスで確かめる
      expect((await api(requesterPage, 'GET', path)).status).toBe(200)
      expect((await api(otherPage, 'GET', path)).status).toBe(404)
    }
    expect((await api(otherPage, 'GET', `/api/t/${slug}/tickets/${number}`)).status).toBe(404)
  })

  await test.step('11. テナント B の管理者は、テナント A のチケットを URL 直打ちでも API でも閲覧できない', async () => {
    const slugB = unique('b')
    const pageB = await newPage(browser)
    await signUpTenant(pageB, slugB, 'テナント B', { name: '管理者 B', email: `admin@${slugB}.example.com` })
    await pageB.goto(`/t/${slug}/agent/tickets/${number}`)
    await expect(pageB.getByText(subject)).toHaveCount(0)
    await pageB.waitForURL(`**/t/${slug}/login**`)
    for (const path of [`/api/t/${slug}/tickets/${number}`, `/api/t/${slug}/tickets/${number}/comments`]) {
      expect((await api(adminPage, 'GET', path)).status).toBe(200)
      expect((await api(pageB, 'GET', path)).status).toBe(401)
    }
    expect((await api(pageB, 'GET', `/api/t/${slugB}/tickets/${number}`)).status).toBe(404)
  })

  await test.step('12. 許可ドメインを設定すると、許可外のアドレスでは依頼者登録できない', async () => {
    await adminPage.locator('nav[aria-label=管理メニュー]').getByText('テナント設定').click()
    await adminPage.click('#signup-domain')
    await adminPage.fill('#allowed-domains', `${slug}.example.net`)
    await adminPage.getByRole('button', { name: '保存する' }).click()
    await expect(adminPage.getByText('設定を保存しました')).toBeVisible()

    const outsider = await newPage(browser)
    await outsider.goto(`/t/${slug}/register`)
    await outsider.fill('#name', '社外 太郎')
    await outsider.fill('#email', `taro@outside-${slug}.example.org`)
    await outsider.fill('#password', PASSWORD)
    await outsider.click('button[type=submit]')
    await expect(outsider.getByText('確認メールを送りました')).toHaveCount(0)
    await expect(outsider.getByText('このメールアドレスでは登録できません')).toBeVisible()
  })

  await test.step('13. 件名の一部（日本語）で検索すると見つかる', async () => {
    await agentPage.fill('input[aria-label="チケットを検索"]', '接続できない')
    await agentPage.press('input[aria-label="チケットを検索"]', 'Enter')
    await expect(agentPage.getByRole('link', { name: subject })).toBeVisible()
  })
})
