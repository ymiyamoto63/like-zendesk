import { ApiError } from '@/api/client'

const byCode: Record<string, string> = {
  'login.invalid_credentials': 'メールアドレスまたはパスワードが正しくありません。',
  'login.too_many_attempts': 'ログインの失敗が続いたため、しばらくログインできません。15 分ほどあけてお試しください。',
  'login.email_unverified':
    'メールアドレスの確認が済んでいません。登録時に届いたメールのリンクを開いてください。',
  'requester.domain_not_allowed': 'このメールアドレスでは登録できません。所属先のメールアドレスをお使いください。',
  'token.invalid': 'リンクが無効か、有効期限が切れています。',
  'slug.taken': 'このテナント識別子はすでに使われています。',
  'slug.reserved': 'このテナント識別子は使えません。別の識別子を指定してください。',
  validation_failed: '入力内容を確認してください。',
}

/** API のエラーを利用者向けの文に変える。 */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return byCode[e.code] ?? `エラーが発生しました（${e.code}）。`
  return '通信に失敗しました。時間をおいてお試しください。'
}
