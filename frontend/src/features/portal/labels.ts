import type { Status } from '@/shared/labels'

/**
 * 依頼者に見せるステータス名。エージェント側の区別（保留と待機など）は依頼者には関係がないので、何を待っているかで言い換える。
 */
export const portalStatusLabels: Record<Status, string> = {
  new: '受付済み',
  open: '対応中',
  pending: 'お客様の返信待ち',
  on_hold: '対応中',
  solved: '解決済み',
  closed: '完了',
}

/** 返信すると状態がどう変わるかの案内（docs/design/frontend.md 3.3）。 */
export function replyNotice(before: Status, after: Status, followUp: boolean): string {
  if (followUp) return 'このお問い合わせは完了していたため、新しいお問い合わせとして受け付けました。'
  if (before !== after && after === 'open') return '返信を送りました。お問い合わせは「対応中」に戻りました。'
  return '返信を送りました。'
}

/** 依頼者が「解決済み」にできるか。担当者がいて、まだ解決・完了していないとき。 */
export function canSolve(status: Status, assigneeName: string | null): boolean {
  return assigneeName !== null && status !== 'solved' && status !== 'closed'
}
