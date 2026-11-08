import type { Priority, Status, TicketType } from '@/shared/labels'
import type { Group, Ticket, TicketChanges } from './queries'

/** 画面で編集中のチケットの項目。 */
export interface TicketDraft {
  status: Status
  priority: Priority
  type: TicketType
  groupId: string | null
  assigneeId: string | null
  tags: string[]
}

export function draftOf(ticket: Ticket): TicketDraft {
  return {
    status: ticket.status,
    priority: ticket.priority,
    type: ticket.type,
    groupId: ticket.group?.id ?? null,
    assigneeId: ticket.assignee?.id ?? null,
    tags: [...ticket.tags],
  }
}

/** 変わった項目だけの更新内容。何も変わっていなければ null。 */
export function changesOf(ticket: Ticket, draft: TicketDraft): TicketChanges | null {
  const before = draftOf(ticket)
  const changes: TicketChanges = { version: ticket.version }
  let changed = false
  if (draft.status !== before.status) {
    changes.status = draft.status
    changed = true
  }
  if (draft.priority !== before.priority) {
    changes.priority = draft.priority
    changed = true
  }
  if (draft.type !== before.type) {
    changes.type = draft.type
    changed = true
  }
  if (draft.groupId !== before.groupId) {
    changes.groupId = draft.groupId
    changed = true
  }
  if (draft.assigneeId !== before.assigneeId) {
    changes.assigneeId = draft.assigneeId
    changed = true
  }
  if (draft.tags.join('\n') !== before.tags.join('\n')) {
    changes.tags = draft.tags
    changed = true
  }
  return changed ? changes : null
}

/** グループのメンバーだけを担当者に選べる（F-T-6）。 */
export function assigneeOptions(groups: Group[], groupId: string | null) {
  return groups.find((g) => g.id === groupId)?.members ?? []
}

/** グループを変えたとき、新しいグループに属さない担当者は外す。 */
export function withGroup(draft: TicketDraft, groups: Group[], groupId: string | null): TicketDraft {
  const members = assigneeOptions(groups, groupId)
  const keep = members.some((m) => m.id === draft.assigneeId)
  return { ...draft, groupId, assigneeId: keep ? draft.assigneeId : null }
}

/** 「vpn, remote」のような入力をタグの配列にする。 */
export function parseTags(text: string): string[] {
  return [
    ...new Set(
      text
        .split(/[,\s、]+/)
        .map((t) => t.trim().toLowerCase())
        .filter((t) => t.length > 0),
    ),
  ]
}
