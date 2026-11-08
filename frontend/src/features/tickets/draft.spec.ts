import { describe, expect, it } from 'vitest'
import { changesOf, draftOf, parseTags, withGroup } from './draft'
import type { Group, Ticket } from './queries'

const ticket: Ticket = {
  number: 1,
  subject: 'VPN',
  status: 'open',
  priority: 'normal',
  type: 'incident',
  requester: { id: 'r', name: '山田' },
  group: { id: 'g1', name: 'インフラ' },
  assignee: { id: 'u1', name: '佐藤' },
  tags: ['vpn'],
  followUpOf: null,
  version: 3,
  createdAt: '2026-11-08T00:00:00Z',
  updatedAt: '2026-11-08T00:00:00Z',
  solvedAt: null,
  closedAt: null,
}

const groups: Group[] = [
  { id: 'g1', name: 'インフラ', members: [{ id: 'u1', name: '佐藤' }] },
  { id: 'g2', name: '経理', members: [{ id: 'u2', name: '鈴木' }] },
]

describe('changesOf', () => {
  it('is null when nothing changed', () => {
    expect(changesOf(ticket, draftOf(ticket))).toBeNull()
  })

  it('sends only the changed fields with the version', () => {
    const draft = { ...draftOf(ticket), status: 'pending' as const, tags: ['vpn', 'remote'] }
    expect(changesOf(ticket, draft)).toEqual({ version: 3, status: 'pending', tags: ['vpn', 'remote'] })
  })

  it('sends null to remove the assignee', () => {
    expect(changesOf(ticket, { ...draftOf(ticket), assigneeId: null })).toEqual({ version: 3, assigneeId: null })
  })
})

describe('withGroup', () => {
  it('drops an assignee who is not in the new group', () => {
    const draft = withGroup(draftOf(ticket), groups, 'g2')
    expect(draft).toMatchObject({ groupId: 'g2', assigneeId: null })
  })
})

describe('parseTags', () => {
  it('splits, lowercases and removes duplicates', () => {
    expect(parseTags('VPN, remote  vpn、在宅')).toEqual(['vpn', 'remote', '在宅'])
  })
})
