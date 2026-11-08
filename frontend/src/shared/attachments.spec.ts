import { describe, expect, it } from 'vitest'
import { attachmentUrl, formatSize } from './attachments'

describe('formatSize', () => {
  it('uses the largest unit that keeps the number readable', () => {
    expect(formatSize(512)).toBe('512 B')
    expect(formatSize(1536)).toBe('1.5 KB')
    expect(formatSize(200 * 1024)).toBe('200 KB')
    expect(formatSize(20 * 1024 * 1024)).toBe('20.0 MB')
  })
})

describe('attachmentUrl', () => {
  it('escapes path segments', () => {
    expect(attachmentUrl('acme', 'a/b')).toBe('/api/t/acme/attachments/a%2Fb')
  })
})
