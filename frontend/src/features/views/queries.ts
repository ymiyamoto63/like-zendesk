import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { api, unwrap } from '@/api/client'
import type { components } from '@/api/schema'

export type View = components['schemas']['ViewResponse']
export type ViewRequest = components['schemas']['ViewRequest']

export const DEFAULT_VIEW = 'builtin:my-unsolved'

const keys = {
  all: (slug: string) => ['views', slug] as const,
}

/** ビューの一覧と件数。チケットが変わったら件数も変わるので、チケットの更新時にも無効化する。 */
export function useViews(slug: string) {
  return useQuery({
    queryKey: keys.all(slug),
    queryFn: () => unwrap(api.GET('/api/t/{slug}/views', { params: { path: { slug }, query: { withCounts: true } } })),
    refetchInterval: 60_000,
  })
}

export function useViewMutations(slug: string) {
  const client = useQueryClient()
  const refresh = () => client.invalidateQueries({ queryKey: keys.all(slug) })
  return {
    create: useMutation({
      mutationFn: (body: ViewRequest) => unwrap(api.POST('/api/t/{slug}/views', { params: { path: { slug } }, body })),
      onSuccess: refresh,
    }),
    update: useMutation({
      mutationFn: ({ id, body }: { id: string; body: ViewRequest }) =>
        unwrap(api.PATCH('/api/t/{slug}/views/{id}', { params: { path: { slug, id } }, body })),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: string) => unwrap(api.DELETE('/api/t/{slug}/views/{id}', { params: { path: { slug, id } } })),
      onSuccess: refresh,
    }),
  }
}
