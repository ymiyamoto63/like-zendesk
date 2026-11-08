import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { api, unwrap } from '@/api/client'
import type { components } from '@/api/schema'

export type PortalTicket = components['schemas']['PortalTicketResponse']
export type PortalComment = components['schemas']['PortalCommentResponse']

export const PORTAL_PAGE_SIZE = 20

export function useMyRequests(slug: string, page: Ref<number>) {
  return useQuery({
    queryKey: computed(() => ['portal', slug, 'list', page.value]),
    queryFn: () =>
      unwrap(
        api.GET('/api/t/{slug}/portal/tickets', {
          params: { path: { slug }, query: { page: page.value, size: PORTAL_PAGE_SIZE } },
        }),
      ),
    placeholderData: keepPreviousData,
  })
}

export function useRequest(slug: string, number: number) {
  const path = { params: { path: { slug, number } } }
  return {
    ticket: useQuery({
      queryKey: ['portal', slug, number],
      queryFn: () => unwrap(api.GET('/api/t/{slug}/portal/tickets/{number}', path)),
    }),
    comments: useQuery({
      queryKey: ['portal', slug, number, 'comments'],
      queryFn: () => unwrap(api.GET('/api/t/{slug}/portal/tickets/{number}/comments', path)),
    }),
  }
}

export function useRequestMutations(slug: string, number: number) {
  const client = useQueryClient()
  const path = { params: { path: { slug, number } } }
  const refresh = () => client.invalidateQueries({ queryKey: ['portal', slug] })
  return {
    reply: useMutation({
      mutationFn: (body: components['schemas']['ReplyRequest']) =>
        unwrap(api.POST('/api/t/{slug}/portal/tickets/{number}/comments', { ...path, body })),
      onSuccess: refresh,
    }),
    solve: useMutation({
      mutationFn: () => unwrap(api.POST('/api/t/{slug}/portal/tickets/{number}/solve', path)),
      onSuccess: refresh,
    }),
  }
}

export function useSubmitRequest(slug: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: components['schemas']['NewRequestRequest']) =>
      unwrap(api.POST('/api/t/{slug}/portal/tickets', { params: { path: { slug } }, body })),
    onSuccess: () => client.invalidateQueries({ queryKey: ['portal', slug] }),
  })
}
