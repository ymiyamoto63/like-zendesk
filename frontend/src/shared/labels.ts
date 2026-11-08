import type { components } from '@/api/schema'

type Ticket = components['schemas']['TicketResponse']
export type Status = Ticket['status']
export type Priority = Ticket['priority']
export type TicketType = Ticket['type']

/** ステータス・優先度・種別の日本語表示名（要件 7、F-T-3、F-T-4）。 */
export const statusLabels: Record<Status, string> = {
  new: '新規',
  open: 'オープン',
  pending: '保留',
  on_hold: '待機',
  solved: '解決済み',
  closed: 'クローズ',
}

export const priorityLabels: Record<Priority, string> = {
  low: '低',
  normal: '通常',
  high: '高',
  urgent: '緊急',
}

export const typeLabels: Record<TicketType, string> = {
  question: '質問',
  incident: 'インシデント',
  problem: '問題',
  task: 'タスク',
}

/** Select の選択肢。表示順は定義順。 */
export function options<K extends string>(labels: Record<K, string>): { value: K; label: string }[] {
  return (Object.keys(labels) as K[]).map((value) => ({ value, label: labels[value] }))
}

/** エージェントが選べるステータス。クローズは自動クローズだけが設定する。 */
export const selectableStatuses = options(statusLabels).filter((o) => o.value !== 'closed')

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ja-JP', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
