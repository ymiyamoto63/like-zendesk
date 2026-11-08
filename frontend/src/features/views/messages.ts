import { ApiError } from '@/api/client'
import { errorMessage } from '@/features/auth/messages'

const byCode: Record<string, string> = {
  'view.invalid_condition': '条件の値が正しくありません。消えたグループや担当者が残っていないか確かめてください。',
  'view.invalid_sort': '並び順の項目が正しくありません。',
}

export function viewErrorMessage(e: unknown): string {
  if (e instanceof ApiError && byCode[e.code]) return byCode[e.code]
  if (e instanceof ApiError && e.status === 404) return 'このビューは見つからないか、変更できません。'
  return errorMessage(e)
}
