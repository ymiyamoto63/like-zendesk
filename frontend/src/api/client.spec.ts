import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, unwrap } from './client'

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('turns problem responses into ApiError with the code', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, { code: 'unauthenticated' })))

    const call = unwrap(api.GET('/api/t/{slug}/me', { params: { path: { slug: 'acme' } } }))

    await expect(call).rejects.toEqual(new ApiError(401, 'unauthenticated'))
  })

  it('fetches the CSRF cookie before the first state-changing request and sends it back', async () => {
    const fetch = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/api/csrf')) {
        document.cookie = 'XSRF-TOKEN=token-123'
        return new Response(null, { status: 204 })
      }
      return new Response(null, { status: 204 })
    })
    vi.stubGlobal('fetch', fetch)

    await unwrap(api.DELETE('/api/t/{slug}/session', { params: { path: { slug: 'acme' } } }))

    expect(String(fetch.mock.calls[0]?.[0])).toContain('/api/csrf')
    const request = fetch.mock.calls[1]?.[0] as Request
    expect(request.headers.get('X-XSRF-TOKEN')).toBe('token-123')
  })

  it('does not send the CSRF header on GET', async () => {
    const fetch = vi.fn().mockResolvedValue(json(200, { slug: 'acme', name: 'Acme', requesterSignup: 'open' }))
    vi.stubGlobal('fetch', fetch)

    await unwrap(api.GET('/api/t/{slug}', { params: { path: { slug: 'acme' } } }))

    expect(fetch).toHaveBeenCalledTimes(1)
    expect((fetch.mock.calls[0]?.[0] as Request).headers.has('X-XSRF-TOKEN')).toBe(false)
  })
})
