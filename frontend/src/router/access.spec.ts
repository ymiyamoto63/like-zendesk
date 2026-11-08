import { describe, expect, it } from 'vitest'
import { access } from './access'

describe('access', () => {
  it('lets anyone open public pages', () => {
    expect(access('public', null)).toBe('allow')
  })

  it('sends visitors who are not logged in to the login page', () => {
    expect(access('requester', null)).toBe('login')
    expect(access('staff', null)).toBe('login')
    expect(access('admin', null)).toBe('login')
  })

  it('sends staff who open the portal to the agent pages', () => {
    expect(access('requester', 'agent')).toBe('agent')
    expect(access('requester', 'admin')).toBe('agent')
    expect(access('requester', 'requester')).toBe('allow')
  })

  it('hides staff pages from requesters', () => {
    expect(access('staff', 'requester')).toBe('not_found')
    expect(access('staff', 'agent')).toBe('allow')
    expect(access('staff', 'admin')).toBe('allow')
  })

  it('hides admin pages from everyone but admins', () => {
    expect(access('admin', 'requester')).toBe('not_found')
    expect(access('admin', 'agent')).toBe('not_found')
    expect(access('admin', 'admin')).toBe('allow')
  })
})
