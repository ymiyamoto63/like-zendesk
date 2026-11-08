import type { components } from '@/api/schema'
import type { Status } from '@/shared/labels'
import { parseTags } from './draft'

export type BulkRequest = components['schemas']['BulkUpdateRequest']
export type BulkResponse = components['schemas']['BulkUpdateResponse']

export const KEEP = 'keep'
export const NONE = 'none'

/** 一括更新パネルの入力。`keep` は変えない、担当者の `none` は外す。 */
export interface BulkForm {
  status: Status | typeof KEEP
  groupId: string | typeof KEEP
  assigneeId: string | typeof NONE | typeof KEEP
  addTags: string
  removeTags: string
}

export function emptyBulkForm(): BulkForm {
  return { status: KEEP, groupId: KEEP, assigneeId: KEEP, addTags: '', removeTags: '' }
}

/** 変える項目だけを入れたリクエスト。何も変えないなら null。 */
export function bulkRequest(numbers: number[], form: BulkForm): BulkRequest | null {
  const request: BulkRequest = { numbers }
  if (form.status !== KEEP) request.status = form.status
  if (form.groupId !== KEEP) request.groupId = form.groupId
  if (form.assigneeId === NONE) request.assigneeId = null
  else if (form.assigneeId !== KEEP) request.assigneeId = form.assigneeId
  const add = parseTags(form.addTags)
  const remove = parseTags(form.removeTags)
  if (add.length > 0) request.addTags = add
  if (remove.length > 0) request.removeTags = remove
  return Object.keys(request).length > 1 ? request : null
}

/** 結果を利用者向けの 1 文にする。 */
export function bulkSummary(result: BulkResponse): string {
  const ok = `${result.updated.length} 件を更新しました。`
  if (result.failed.length === 0) return ok
  const numbers = result.failed.map((f) => `#${f.number}`).join('、')
  return `${ok}${numbers} は条件に合わないため更新できませんでした（担当者のいないチケットは解決にできない、など）。`
}
