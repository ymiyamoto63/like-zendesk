import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { api, unwrap } from '@/api/client'
import type { components } from '@/api/schema'

export type AdminUser = components['schemas']['AdminUserResponse']
export type UserChanges = components['schemas']['UpdateUserRequest']
export type Organization = components['schemas']['OrganizationResponse']
export type Settings = components['schemas']['SettingsRequest']

export const USERS_PAGE_SIZE = 50

/** ユーザー一覧の条件。`all` は「絞り込まない」。 */
export type UserQuery = {
  role: 'all' | AdminUser['role']
  status: 'all' | AdminUser['status']
  q: string
  page: number
}

const keys = {
  users: (slug: string) => ['admin', slug, 'users'] as const,
  organizations: (slug: string) => ['organizations', slug] as const,
  settings: (slug: string) => ['admin', slug, 'settings'] as const,
  groups: (slug: string) => ['groups', slug] as const,
}

export function useAdminUsers(slug: string, query: Ref<UserQuery>) {
  return useQuery({
    queryKey: computed(() => [...keys.users(slug), query.value]),
    queryFn: () =>
      unwrap(
        api.GET('/api/t/{slug}/admin/users', {
          params: {
            path: { slug },
            query: {
              role: query.value.role === 'all' ? undefined : query.value.role,
              status: query.value.status === 'all' ? undefined : query.value.status,
              q: query.value.q || undefined,
              page: query.value.page,
              size: USERS_PAGE_SIZE,
            },
          },
        }),
      ),
    placeholderData: keepPreviousData,
  })
}

export function useUserMutations(slug: string) {
  const client = useQueryClient()
  const refresh = () => {
    void client.invalidateQueries({ queryKey: keys.users(slug) })
    // 所属人数とグループの候補が変わる
    void client.invalidateQueries({ queryKey: keys.organizations(slug) })
    void client.invalidateQueries({ queryKey: keys.groups(slug) })
  }
  return {
    update: useMutation({
      mutationFn: ({ id, changes }: { id: string; changes: UserChanges }) =>
        unwrap(api.PATCH('/api/t/{slug}/admin/users/{id}', { params: { path: { slug, id } }, body: changes })),
      onSuccess: refresh,
    }),
    invite: useMutation({
      mutationFn: (body: components['schemas']['InviteRequest']) =>
        unwrap(api.POST('/api/t/{slug}/admin/invitations', { params: { path: { slug } }, body })),
      onSuccess: refresh,
    }),
  }
}

/** 所属組織の一覧。ビューの条件にも使うので、スタッフ全員が読める。 */
export function useOrganizations(slug: string) {
  return useQuery({
    queryKey: keys.organizations(slug),
    queryFn: () => unwrap(api.GET('/api/t/{slug}/organizations', { params: { path: { slug } } })),
    staleTime: 60_000,
  })
}

export function useOrganizationMutations(slug: string) {
  const client = useQueryClient()
  const refresh = () => {
    void client.invalidateQueries({ queryKey: keys.organizations(slug) })
    void client.invalidateQueries({ queryKey: keys.users(slug) })
  }
  return {
    create: useMutation({
      mutationFn: (name: string) =>
        unwrap(api.POST('/api/t/{slug}/admin/organizations', { params: { path: { slug } }, body: { name } })),
      onSuccess: refresh,
    }),
    rename: useMutation({
      mutationFn: ({ id, name }: { id: string; name: string }) =>
        unwrap(
          api.PATCH('/api/t/{slug}/admin/organizations/{id}', { params: { path: { slug, id } }, body: { name } }),
        ),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: string) =>
        unwrap(api.DELETE('/api/t/{slug}/admin/organizations/{id}', { params: { path: { slug, id } } })),
      onSuccess: refresh,
    }),
  }
}

export function useGroupMutations(slug: string) {
  const client = useQueryClient()
  const refresh = () => client.invalidateQueries({ queryKey: keys.groups(slug) })
  return {
    create: useMutation({
      mutationFn: (name: string) =>
        unwrap(api.POST('/api/t/{slug}/admin/groups', { params: { path: { slug } }, body: { name } })),
      onSuccess: refresh,
    }),
    rename: useMutation({
      mutationFn: ({ id, name }: { id: string; name: string }) =>
        unwrap(api.PATCH('/api/t/{slug}/admin/groups/{id}', { params: { path: { slug, id } }, body: { name } })),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: string) =>
        unwrap(api.DELETE('/api/t/{slug}/admin/groups/{id}', { params: { path: { slug, id } } })),
      onSuccess: refresh,
    }),
    setMembers: useMutation({
      mutationFn: ({ id, userIds }: { id: string; userIds: string[] }) =>
        unwrap(
          api.PUT('/api/t/{slug}/admin/groups/{id}/members', { params: { path: { slug, id } }, body: { userIds } }),
        ),
      onSuccess: refresh,
    }),
  }
}

export function useSettings(slug: string) {
  return useQuery({
    queryKey: keys.settings(slug),
    queryFn: () => unwrap(api.GET('/api/t/{slug}/admin/settings', { params: { path: { slug } } })),
  })
}

export function useSettingsMutation(slug: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: Settings) =>
      unwrap(api.PATCH('/api/t/{slug}/admin/settings', { params: { path: { slug } }, body })),
    onSuccess: (saved) => client.setQueryData(keys.settings(slug), saved),
  })
}

/** グループのメンバー候補: 有効なエージェントと管理者。 */
export function useActiveStaff(slug: string) {
  const page = (role: 'agent' | 'admin') =>
    unwrap(
      api.GET('/api/t/{slug}/admin/users', {
        params: { path: { slug }, query: { role, status: 'active', size: 100 } },
      }),
    )
  return useQuery({
    queryKey: [...keys.users(slug), 'active-staff'],
    queryFn: async () => {
      const [agents, admins] = await Promise.all([page('agent'), page('admin')])
      return [...agents.items, ...admins.items].sort((a, b) => a.name.localeCompare(b.name, 'ja'))
    },
  })
}
