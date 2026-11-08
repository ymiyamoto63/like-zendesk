import { errorMessage } from '@/features/auth/messages'
import { ApiError } from '@/api/client'

const byCode: Record<string, string> = {
  'status.closed_is_final': 'クローズしたチケットは変更できません。',
  'status.cannot_return_to_new': '担当者のいるチケットや対応を始めたチケットは「新規」に戻せません。',
  'status.solved_requires_assignee': '解決済みにするには担当者を設定してください。',
  'status.closed_by_system_only': 'クローズは解決から 4 日後に自動で行われます。',
  'status.reopen_before_changing': '解決済みのチケットは、先に「オープン」に戻してください。',
  'assignee.requires_group': '担当者を設定するには、先にグループを選んでください。',
  'assignee.not_in_group': '担当者は選んだグループのメンバーから選んでください。',
  'assignee.not_found': '担当者が見つかりません。',
  'group.not_found': 'グループが見つかりません。',
  'ticket.requester_not_found': '依頼者が見つかりません。',
}

export function ticketErrorMessage(e: unknown): string {
  if (e instanceof ApiError && byCode[e.code]) return byCode[e.code]
  return errorMessage(e)
}
