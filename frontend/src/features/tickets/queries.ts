import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { api, unwrap } from '@/api/client'
import type { components } from '@/api/schema'

export type Ticket = components['schemas']['TicketResponse']
export type Comment = components['schemas']['CommentResponse']
export type ChangeSet = components['schemas']['ChangeSetResponse']
export type Group = components['schemas']['GroupResponse']
export type UserSummary = components['schemas']['UserSummary']
export type TicketChanges = components['schemas']['UpdateTicketRequest']
export type NewTicket = components['schemas']['CreateTicketRequest']

export const PAGE_SIZE = 25

const keys = {
  list: (slug: string, query: ListQuery) => ['tickets', slug, 'list', query] as const,
  ticket: (slug: string, number: number) => ['tickets', slug, number] as const,
  comments: (slug: string, number: number) => ['tickets', slug, number, 'comments'] as const,
  events: (slug: string, number: number) => ['tickets', slug, number, 'events'] as const,
  groups: (slug: string) => ['groups', slug] as const,
}

/** 一覧の条件。URL のクエリと 1 対 1 に対応する（docs/design/frontend.md 3.1）。`page` は 0 から。 */
export type ListQuery = { view: string; q: string; sort: string; page: number }

export function useTicketList(slug: string, query: Ref<ListQuery>) {
  return useQuery({
    queryKey: computed(() => keys.list(slug, query.value)),
    queryFn: () =>
      unwrap(
        api.GET('/api/t/{slug}/tickets', {
          params: {
            path: { slug },
            query: {
              view: query.value.view,
              q: query.value.q || undefined,
              sort: query.value.sort || undefined,
              page: query.value.page,
              size: PAGE_SIZE,
            },
          },
        }),
      ),
    placeholderData: keepPreviousData,
  })
}

export function useTicket(slug: string, number: number) {
  const path = { params: { path: { slug, number } } }
  return {
    ticket: useQuery({
      queryKey: keys.ticket(slug, number),
      queryFn: () => unwrap(api.GET('/api/t/{slug}/tickets/{number}', path)),
    }),
    comments: useQuery({
      queryKey: keys.comments(slug, number),
      queryFn: () => unwrap(api.GET('/api/t/{slug}/tickets/{number}/comments', path)),
    }),
    events: useQuery({
      queryKey: keys.events(slug, number),
      queryFn: () => unwrap(api.GET('/api/t/{slug}/tickets/{number}/events', path)),
    }),
  }
}

export function useGroups(slug: string) {
  return useQuery({
    queryKey: keys.groups(slug),
    queryFn: () => unwrap(api.GET('/api/t/{slug}/groups', { params: { path: { slug } } })),
    staleTime: 60_000,
  })
}

export function searchUsers(slug: string, q: string, role?: UserSummary['role']) {
  return unwrap(api.GET('/api/t/{slug}/users', { params: { path: { slug }, query: { q, role } } }))
}

/** チケットの更新と、コメントの追加（項目の更新を伴うこともある）。成功したら詳細と一覧を読み直す。 */
export function useTicketMutations(slug: string, number: number) {
  const client = useQueryClient()
  const path = { params: { path: { slug, number } } }
  const refresh = (ticket: Ticket) => {
    client.setQueryData(keys.ticket(slug, number), ticket)
    void client.invalidateQueries({ queryKey: ['tickets', slug] })
    void client.invalidateQueries({ queryKey: ['views', slug] })
  }
  return {
    update: useMutation({
      mutationFn: (body: TicketChanges) => unwrap(api.PATCH('/api/t/{slug}/tickets/{number}', { ...path, body })),
      onSuccess: refresh,
    }),
    comment: useMutation({
      mutationFn: (body: components['schemas']['AddCommentRequest']) =>
        unwrap(api.POST('/api/t/{slug}/tickets/{number}/comments', { ...path, body })),
      onSuccess: (result) => refresh(result.ticket),
    }),
    reload: () => client.invalidateQueries({ queryKey: ['tickets', slug, number] }),
  }
}

export function useCreateTicket(slug: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: NewTicket) => unwrap(api.POST('/api/t/{slug}/tickets', { params: { path: { slug } }, body })),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['tickets', slug] })
      await client.invalidateQueries({ queryKey: ['views', slug] })
    },
  })
}
