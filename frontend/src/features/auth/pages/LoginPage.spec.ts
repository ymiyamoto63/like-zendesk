import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import LoginPage from './LoginPage.vue'

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const me = { id: 'u1', name: '佐藤', email: 'sato@example.com', role: 'agent', groups: [] }

async function mountAt(path: string, login: Response) {
  document.cookie = 'XSRF-TOKEN=t'
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: Request) => {
      const url = new URL(input.url)
      if (url.pathname === '/api/t/acme') return json(200, { slug: 'acme', name: 'Acme 社', requesterSignup: 'open' })
      if (url.pathname === '/api/t/acme/session') return login
      return json(404, { code: 'not_found' })
    }),
  )
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/t/:slug/login', component: LoginPage },
      { path: '/t/:slug/agent', component: { template: '<p>agent</p>' } },
      { path: '/t/:slug', component: { template: '<p>portal</p>' } },
      { path: '/:pathMatch(.*)*', name: 'not-found', component: { template: '<p>404</p>' } },
    ],
  })
  await router.push(path)
  const wrapper = mount(LoginPage, { global: { plugins: [createPinia(), router, PrimeVue] } })
  await flushPromises()
  return { wrapper, router }
}

async function submit(wrapper: Awaited<ReturnType<typeof mountAt>>['wrapper']) {
  await wrapper.get('#email').setValue('sato@example.com')
  await wrapper.get('#password').setValue('secret-pass')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

describe('LoginPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows the tenant name', async () => {
    const { wrapper } = await mountAt('/t/acme/login', json(200, me))
    expect(wrapper.get('h1').text()).toBe('Acme 社 にログイン')
  })

  it('opens the agent pages after a staff member logs in', async () => {
    const { wrapper, router } = await mountAt('/t/acme/login', json(200, me))
    await submit(wrapper)
    expect(router.currentRoute.value.fullPath).toBe('/t/acme/agent')
  })

  it('returns to the page the user asked for', async () => {
    const { wrapper, router } = await mountAt('/t/acme/login?redirect=/t/acme/agent/tickets/1', json(200, me))
    await submit(wrapper)
    expect(router.currentRoute.value.fullPath).toBe('/t/acme/agent/tickets/1')
  })

  it('ignores a redirect to another tenant', async () => {
    const { wrapper, router } = await mountAt('/t/acme/login?redirect=/t/other/agent', json(200, me))
    await submit(wrapper)
    expect(router.currentRoute.value.fullPath).toBe('/t/acme/agent')
  })

  it('ignores a redirect to a tenant whose slug starts with the same letters', async () => {
    const { wrapper, router } = await mountAt('/t/acme/login?redirect=/t/acme-evil/agent', json(200, me))
    await submit(wrapper)
    expect(router.currentRoute.value.fullPath).toBe('/t/acme/agent')
  })

  it('explains a failed login', async () => {
    const { wrapper } = await mountAt('/t/acme/login', json(401, { code: 'login.invalid_credentials' }))
    await submit(wrapper)
    expect(wrapper.text()).toContain('メールアドレスまたはパスワードが正しくありません。')
  })
})
