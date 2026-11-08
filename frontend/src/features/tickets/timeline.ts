import {
  priorityLabels,
  statusLabels,
  typeLabels,
  type Priority,
  type Status,
  type TicketType,
} from '@/shared/labels'
import type { ChangeSet, Comment } from './queries'

export type TimelineItem =
  | { kind: 'comment'; at: string; comment: Comment }
  | { kind: 'created'; at: string; actor: string }
  | { kind: 'change'; at: string; actor: string; lines: string[] }

const fieldLabels: Record<string, string> = {
  subject: '件名',
  status: '状態',
  priority: '優先度',
  type: '種別',
  group: 'グループ',
  assignee: '担当者',
  tags: 'タグ',
}

function actorName(set: ChangeSet): string {
  return set.actor?.name ?? 'システム'
}

/** 監査ログの値を表示用の文字列にする。値の形は項目によって違う（文字列、文字列の配列、{id, name}）。 */
export function formatValue(field: string, value: unknown): string {
  if (value === null || value === undefined) return 'なし'
  if (Array.isArray(value)) return value.length === 0 ? 'なし' : value.join(', ')
  if (typeof value === 'object' && 'name' in value) return String(value.name)
  const text = String(value)
  switch (field) {
    case 'status':
      return statusLabels[text as Status] ?? text
    case 'priority':
      return priorityLabels[text as Priority] ?? text
    case 'type':
      return typeLabels[text as TicketType] ?? text
    default:
      return text
  }
}

/**
 * コメントと監査ログを 1 本の時系列にする（F-L-1）。
 *
 * 作成時の項目と、コメントの追加そのものはコメントの表示と重なるので行にしない。同じ時刻ではコメントを先に置く（返信して保留にした場合など）。
 */
export function buildTimeline(comments: Comment[], events: ChangeSet[]): TimelineItem[] {
  const items: TimelineItem[] = comments.map((comment) => ({ kind: 'comment', at: comment.createdAt, comment }))
  for (const set of events) {
    if (set.changes.some((c) => c.field === 'created')) {
      items.push({ kind: 'created', at: set.createdAt, actor: actorName(set) })
      continue
    }
    const lines = set.changes
      .filter((c) => c.field in fieldLabels)
      .map((c) => `${fieldLabels[c.field]}: ${formatValue(c.field, c.from)} → ${formatValue(c.field, c.to)}`)
    if (lines.length > 0) items.push({ kind: 'change', at: set.createdAt, actor: actorName(set), lines })
  }
  const order = { created: 0, comment: 1, change: 2 }
  return items
    .map((item, index) => ({ item, index }))
    .sort((a, b) => a.item.at.localeCompare(b.item.at) || order[a.item.kind] - order[b.item.kind] || a.index - b.index)
    .map(({ item }) => item)
}
