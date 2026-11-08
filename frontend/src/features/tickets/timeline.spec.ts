import { describe, expect, it } from 'vitest'
import type { ChangeSet, Comment } from './queries'
import { buildTimeline, formatValue } from './timeline'

const sato = { id: 'u1', name: '佐藤' }

function comment(id: string, at: string, isPublic = true): Comment {
  return { id, author: sato, body: id, public: isPublic, createdAt: at }
}

function changes(at: string, ...list: [string, unknown, unknown][]): ChangeSet {
  return {
    changeId: at,
    actor: sato,
    createdAt: at,
    changes: list.map(([field, from, to]) => ({ field, from, to })),
  }
}

describe('buildTimeline', () => {
  it('interleaves comments and field changes in time order', () => {
    const items = buildTimeline(
      [comment('body', '2026-11-08T01:00:00Z'), comment('reply', '2026-11-08T03:00:00Z')],
      [
        changes('2026-11-08T01:00:00Z', ['created', null, { number: 1 }], ['status', null, 'new'], ['comment', null, {}]),
        changes('2026-11-08T02:00:00Z', ['assignee', null, { id: 'u1', name: '佐藤' }], ['status', 'new', 'open']),
        changes('2026-11-08T03:00:00Z', ['status', 'open', 'pending'], ['comment', null, {}]),
      ],
    )

    expect(items.map((i) => i.kind)).toEqual(['created', 'comment', 'change', 'comment', 'change'])
    expect(items[2]).toMatchObject({ lines: ['担当者: なし → 佐藤', '状態: 新規 → オープン'] })
    expect(items[4]).toMatchObject({ lines: ['状態: オープン → 保留'] })
  })

  it('leaves out change sets that only added a comment', () => {
    const items = buildTimeline([comment('note', '2026-11-08T01:00:00Z', false)], [changes('2026-11-08T01:00:00Z', ['comment', null, {}])])
    expect(items.map((i) => i.kind)).toEqual(['comment'])
  })

  it('names the system when there is no actor', () => {
    const items = buildTimeline([], [{ ...changes('2026-11-12T00:00:00Z', ['status', 'solved', 'closed']), actor: null }])
    expect(items[0]).toMatchObject({ actor: 'システム', lines: ['状態: 解決済み → クローズ'] })
  })
})

describe('formatValue', () => {
  it('formats each kind of value', () => {
    expect(formatValue('priority', 'urgent')).toBe('緊急')
    expect(formatValue('tags', ['vpn', 'remote'])).toBe('vpn, remote')
    expect(formatValue('tags', [])).toBe('なし')
    expect(formatValue('group', null)).toBe('なし')
    expect(formatValue('subject', '件名')).toBe('件名')
  })
})
