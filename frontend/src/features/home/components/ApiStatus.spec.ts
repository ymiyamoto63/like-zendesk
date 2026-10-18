import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ApiStatus from './ApiStatus.vue'

function respond(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })))
}

describe('ApiStatus', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows that the API is up when health reports UP', async () => {
    respond(200, { status: 'UP' })
    const wrapper = mount(ApiStatus)
    await flushPromises()
    expect(wrapper.text()).toBe('API: 稼働中')
  })

  it('shows that the API is down when health reports DOWN', async () => {
    respond(503, { status: 'DOWN' })
    const wrapper = mount(ApiStatus)
    await flushPromises()
    expect(wrapper.text()).toBe('API: 応答なし')
  })

  it('shows that the API is down when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')))
    const wrapper = mount(ApiStatus)
    await flushPromises()
    expect(wrapper.text()).toBe('API: 応答なし')
  })
})
