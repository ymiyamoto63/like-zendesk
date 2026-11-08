import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import CommentComposer from '../components/CommentComposer.vue'
import type { Ticket } from '../queries'
import TicketDetailPage from './TicketDetailPage.vue'

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function ticket(version: number, extra: Partial<Ticket> = {}): Ticket {
  return {
    number: 7,
    subject: 'VPN に接続できない',
    status: 'open',
    priority: 'normal',
    type: 'incident',
    requester: { id: 'r', name: '山田 花子' },
    group: { id: 'g1', name: 'インフラ' },
    assignee: { id: 'u1', name: '佐藤' },
    tags: [],
    followUpOf: null,
    version,
    createdAt: '2026-11-08T00:00:00Z',
    updatedAt: '2026-11-08T00:00:00Z',
    solvedAt: null,
    closedAt: null,
    ...extra,
  }
}

const comments = [
  { id: 'c1', author: { id: 'u1', name: '佐藤' }, body: '本文です', public: true, createdAt: '2026-11-08T00:00:00Z', attachments: [] },
  { id: 'c2', author: { id: 'u1', name: '佐藤' }, body: 'ベンダー確認中', public: false, createdAt: '2026-11-08T01:00:00Z', attachments: [] },
]

async function mountPage(onComment: (body: unknown) => Response) {
  document.cookie = 'XSRF-TOKEN=t'
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener() {}, removeEventListener() {} }))
  let current = ticket(1)
  const posted: unknown[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: Request) => {
      const key = `${input.method} ${new URL(input.url).pathname}`
      switch (key) {
        case 'GET /api/t/acme/tickets/7':
          return json(200, current)
        case 'GET /api/t/acme/tickets/7/comments':
          return json(200, comments)
        case 'GET /api/t/acme/tickets/7/events':
          return json(200, { items: [] })
        case 'GET /api/t/acme/groups':
          return json(200, [{ id: 'g1', name: 'インフラ', members: [{ id: 'u1', name: '佐藤' }] }])
        case 'POST /api/t/acme/tickets/7/comments': {
          const body = await input.json()
          posted.push(body)
          const response = onComment(body)
          if (response.status === 409) current = ticket(2, { priority: 'high' })
          return response
        }
      }
      return json(404, { code: 'not_found' })
    }),
  )
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/t/:slug/agent/tickets/:number', component: TicketDetailPage }],
  })
  await router.push('/t/acme/agent/tickets/7')
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(TicketDetailPage, {
    global: { plugins: [createPinia(), router, PrimeVue, [VueQueryPlugin, { queryClient }]] },
  })
  await flushPromises()
  return { wrapper, posted }
}

describe('TicketDetailPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('marks internal notes apart from public comments', async () => {
    const { wrapper } = await mountPage(() => json(500, {}))
    const articles = wrapper.findAll('article.comment')
    expect(articles).toHaveLength(2)
    expect(articles[0]!.classes()).not.toContain('internal')
    expect(articles[1]!.classes()).toContain('internal')
    expect(articles[1]!.text()).toContain('社内メモ')
  })

  it('sends the reply with the chosen status in one request', async () => {
    const { wrapper, posted } = await mountPage(() =>
      json(201, { comment: comments[0], ticket: ticket(2, { status: 'pending' }) }),
    )
    await wrapper.get('#comment-body').setValue('ログを添付してください。')
    wrapper.findComponent(CommentComposer).vm.$emit('update:status', 'pending')
    await wrapper.get('form.composer').trigger('submit')
    await flushPromises()
    expect(posted).toEqual([{
        body: 'ログを添付してください。',
        public: true,
        ticket: { version: 1, status: 'pending' },
        attachmentIds: [],
      },])
    expect((wrapper.get('#comment-body').element as HTMLTextAreaElement).value).toBe('')
  })

  it('keeps the typed comment and reloads when someone else updated the ticket', async () => {
    const { wrapper } = await mountPage(() => json(409, { code: 'ticket.version_conflict', currentVersion: 2 }))
    await wrapper.get('#comment-body').setValue('確認します')
    await wrapper.get('form.composer').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('他の人がこのチケットを更新しました')
    expect((wrapper.get('#comment-body').element as HTMLTextAreaElement).value).toBe('確認します')
  })
})
