<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import LogoutButton from '@/features/auth/components/LogoutButton.vue'
import { useSessionStore } from '@/session/store'

const slug = String(useRoute().params.slug)
const me = computed(() => useSessionStore().me[slug])
</script>

<template>
  <div class="agent">
    <header class="bar">
      <nav>
        <RouterLink :to="`/t/${slug}/agent/tickets`">
          チケット
        </RouterLink>
        <RouterLink :to="`/t/${slug}/agent/tickets/new`">
          新規チケット
        </RouterLink>
      </nav>
      <span class="me">{{ me?.name }}（{{ me?.role === 'admin' ? '管理者' : 'エージェント' }}）</span>
      <LogoutButton :slug="slug" />
    </header>
    <main class="content">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.bar {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.5rem 1.5rem;
  background: var(--p-content-background);
  border-bottom: 1px solid var(--p-content-border-color);
}
nav {
  display: flex;
  gap: 1.25rem;
  margin-right: auto;
}
nav a {
  color: var(--p-text-color);
  text-decoration: none;
  font-weight: 600;
}
nav a.router-link-exact-active {
  color: var(--p-primary-color);
}
.me {
  color: var(--p-text-muted-color);
}
.content {
  padding: 1.5rem;
}
</style>
