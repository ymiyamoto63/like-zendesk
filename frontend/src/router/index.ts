import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('@/features/home/pages/HomePage.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('@/shared/pages/NotFoundPage.vue') },
  ],
})
