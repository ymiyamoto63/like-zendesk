import { describe, expect, it } from 'vitest'
import { ME, newCondition, opsByField, problem, valueKind, withField, withOp } from './conditions'

describe('view conditions', () => {
  it('starts each field with its first operator and an empty value', () => {
    expect(newCondition('status')).toEqual({ field: 'status', op: 'in', value: [] })
    expect(newCondition('assignee')).toEqual({ field: 'assignee', op: 'eq', value: ME })
    expect(newCondition('updated_at')).toEqual({ field: 'updated_at', op: 'within_days', value: 7 })
  })

  it('offers only operators the API accepts', () => {
    expect(opsByField.tags).toEqual(['has_any', 'has_all'])
    expect(opsByField.group).toContain('is_null')
    expect(opsByField.status).not.toContain('eq')
  })

  it('keeps the value when the operator takes the same kind of value', () => {
    const c = { field: 'status' as const, op: 'in' as const, value: ['open'] }
    expect(withOp(c, 'not_in').value).toEqual(['open'])
    const g = { field: 'group' as const, op: 'in' as const, value: ['x'] }
    expect(withOp(g, 'is_null').value).toBeNull()
    expect(withOp(withOp(g, 'is_null'), 'in').value).toEqual([])
  })

  it('resets when the field changes', () => {
    const c = { field: 'status' as const, op: 'not_in' as const, value: ['open'] }
    expect(withField(c, 'status')).toBe(c)
    expect(withField(c, 'priority')).toEqual({ field: 'priority', op: 'in', value: [] })
  })

  it('maps operators to inputs', () => {
    expect(valueKind('is_not_null')).toBe('none')
    expect(valueKind('eq')).toBe('user')
    expect(valueKind('has_all')).toBe('tags')
    expect(valueKind('older_than_days')).toBe('days')
    expect(valueKind('in')).toBe('choices')
  })

  it('explains what is missing before saving', () => {
    expect(problem([newCondition('status')])).toBe('「状態」の値を選んでください。')
    expect(problem([{ field: 'created_at', op: 'within_days', value: 0 }])).toContain('1〜3650')
    expect(problem([{ field: 'created_at', op: 'within_days', value: 2.5 }])).toContain('1〜3650')
    expect(problem([{ field: 'group', op: 'is_null', value: null }])).toBeNull()
    expect(problem([{ field: 'tags', op: 'has_any', value: ['vpn'] }])).toBeNull()
  })
})
