import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { useSessionStore } from '@/session/store'
import { access, type Requires } from './access'

declare module 'vue-router' {
  interface RouteMeta {
    requires?: Requires
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('@/features/home/pages/HomePage.vue') },
    { path: '/signup', component: () => import('@/features/auth/pages/SignupPage.vue') },
    { path: '/t/:slug/login', component: () => import('@/features/auth/pages/LoginPage.vue') },
    {
      path: '/t/:slug',
      component: () => import('@/features/portal/pages/PortalHomePage.vue'),
      meta: { requires: 'requester' },
    },
    {
      path: '/t/:slug/agent',
      component: () => import('@/features/agent/pages/AgentHomePage.vue'),
      meta: { requires: 'staff' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/shared/pages/NotFoundPage.vue'),
    },
  ],
})

router.beforeEach(async (to: RouteLocationNormalized) => {
  const requires = to.meta.requires ?? 'public'
  if (requires === 'public') return true
  const slug = String(to.params.slug)
  const me = await useSessionStore().load(slug)
  switch (access(requires, me?.role ?? null)) {
    case 'allow':
      return true
    case 'login':
      return { path: `/t/${slug}/login`, query: { redirect: to.fullPath } }
    case 'agent':
      return { path: `/t/${slug}/agent` }
    case 'not_found':
      return {
        name: 'not-found',
        params: { pathMatch: to.path.slice(1).split('/') },
        query: to.query,
        hash: to.hash,
      }
  }
})
