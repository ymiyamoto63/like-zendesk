import { describe, expect, it } from 'vitest'
import { canSolve, portalStatusLabels, replyNotice } from './labels'

describe('portal labels', () => {
  it('uses requester-friendly status names', () => {
    expect(portalStatusLabels.pending).toBe('お客様の返信待ち')
    expect(portalStatusLabels.on_hold).toBe(portalStatusLabels.open)
  })

  it('explains what a reply did', () => {
    expect(replyNotice('pending', 'open', false)).toContain('「対応中」に戻りました')
    expect(replyNotice('solved', 'open', false)).toContain('「対応中」に戻りました')
    expect(replyNotice('open', 'open', false)).toBe('返信を送りました。')
    expect(replyNotice('closed', 'new', true)).toContain('新しいお問い合わせ')
  })

  it('lets requesters solve only assigned, unsolved tickets', () => {
    expect(canSolve('open', '佐藤')).toBe(true)
    expect(canSolve('new', null)).toBe(false)
    expect(canSolve('solved', '佐藤')).toBe(false)
    expect(canSolve('closed', '佐藤')).toBe(false)
  })
})
