import { describe, expect, it } from 'vitest'
import { formatSort, parseSort } from './sort'

describe('sort', () => {
  it('reads a leading minus as descending', () => {
    expect(parseSort('-updatedAt')).toEqual({ field: 'updatedAt', order: -1 })
    expect(parseSort('priority')).toEqual({ field: 'priority', order: 1 })
    expect(parseSort('')).toBeNull()
    expect(parseSort(undefined)).toBeNull()
  })

  it('round-trips', () => {
    expect(formatSort(parseSort('-createdAt'))).toBe('-createdAt')
    expect(formatSort({ field: 'number', order: 1 })).toBe('number')
    expect(formatSort(null)).toBe('')
  })
})
