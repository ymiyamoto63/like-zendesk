import { describe, expect, it } from 'vitest'
import { bulkRequest, bulkSummary, emptyBulkForm } from './bulk'

describe('bulkRequest', () => {
  it('returns null when nothing changes', () => {
    expect(bulkRequest([1, 2], emptyBulkForm())).toBeNull()
  })

  it('sends only the changed fields', () => {
    const form = { ...emptyBulkForm(), status: 'pending' as const, addTags: 'VPN, 至急' }
    expect(bulkRequest([1, 2], form)).toEqual({ numbers: [1, 2], status: 'pending', addTags: ['vpn', '至急'] })
  })

  it('distinguishes clearing the assignee from keeping it', () => {
    expect(bulkRequest([3], { ...emptyBulkForm(), assigneeId: 'none' })).toEqual({ numbers: [3], assigneeId: null })
    expect(bulkRequest([3], { ...emptyBulkForm(), groupId: 'g1', assigneeId: 'u1' })).toEqual({
      numbers: [3],
      groupId: 'g1',
      assigneeId: 'u1',
    })
  })
})

describe('bulkSummary', () => {
  it('lists the tickets that could not be updated', () => {
    expect(bulkSummary({ updated: [], failed: [] })).toBe('0 件を更新しました。')
    expect(
      bulkSummary({ updated: [], failed: [{ number: 4, code: 'x' }, { number: 9, code: 'y' }] }),
    ).toContain('#4、#9 は')
  })
})
