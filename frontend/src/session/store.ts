import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, ApiError, unwrap, type Me } from '@/api/client'
import type { components } from '@/api/schema'

export type SignupRequest = components['schemas']['SignupRequest']

/**
 * テナントごとのログイン中のユーザー。ログイン状態はテナント単位なので（F-X-2）、テナント識別子をキーにする。
 * 値が null なら、そのテナントにはログインしていない。
 */
export const useSessionStore = defineStore('session', () => {
  const me = ref<Record<string, Me | null>>({})

  async function load(slug: string): Promise<Me | null> {
    if (slug in me.value) return me.value[slug] ?? null
    try {
      me.value[slug] = await unwrap(api.GET('/api/t/{slug}/me', { params: { path: { slug } } }))
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) throw e
      me.value[slug] = null
    }
    return me.value[slug] ?? null
  }

  async function login(slug: string, email: string, password: string): Promise<Me> {
    const user = await unwrap(
      api.POST('/api/t/{slug}/session', { params: { path: { slug } }, body: { email, password } }),
    )
    me.value[slug] = user
    return user
  }

  async function logout(slug: string): Promise<void> {
    await unwrap(api.DELETE('/api/t/{slug}/session', { params: { path: { slug } } }))
    me.value[slug] = null
  }

  /** 招待の受諾やメールアドレスの確認で、API がログインさせたユーザーを記録する。 */
  function loggedIn(slug: string, user: Me): void {
    me.value[slug] = user
  }

  async function signup(body: SignupRequest): Promise<Me> {
    const created = await unwrap(api.POST('/api/tenants', { body }))
    me.value[created.slug] = created.me
    return created.me
  }

  return { me, load, login, logout, loggedIn, signup }
})
