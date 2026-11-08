import { ApiError } from '@/api/client'
import { errorMessage } from '@/features/auth/messages'

const byCode: Record<string, string> = {
  'user.cannot_change_self': '自分自身のロールの変更や無効化はできません。',
  'user.role_change_not_allowed': '依頼者とスタッフの間でロールは変えられません。',
  'user.organization_for_requesters': '所属組織を設定できるのは依頼者だけです。',
  'user.last_admin': '有効な管理者が 1 人以上必要です。',
  'user.email_taken': 'このメールアドレスのユーザーはすでにいます。',
  'organization.name_taken': '同じ名前の組織があります。',
  'organization.not_found': 'その組織は見つかりません。',
  'group.name_taken': '同じ名前のグループがあります。',
  'group.in_use': 'このグループにはチケットが割り当てられているため削除できません。先にチケットを別のグループへ移してください。',
  'group.member_has_solved_tickets': '外そうとしたメンバーが、このグループの解決済みチケットを担当しています。',
  'group.member_must_be_staff': 'メンバーにできるのは有効なエージェントと管理者だけです。',
}

export function adminErrorMessage(e: unknown): string {
  if (e instanceof ApiError && byCode[e.code]) return byCode[e.code]
  if (e instanceof ApiError && e.status === 404) return '対象が見つかりません。ほかの管理者が変更した可能性があります。'
  return errorMessage(e)
}

export const roleLabels = { requester: '依頼者', agent: 'エージェント', admin: '管理者' } as const

export const userStatusLabels = {
  invited: '招待中',
  unverified: 'メール未確認',
  active: '有効',
  inactive: '無効',
} as const
