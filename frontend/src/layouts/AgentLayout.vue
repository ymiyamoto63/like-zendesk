<script setup lang="ts">
import InputText from 'primevue/inputtext'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import LogoutButton from '@/features/auth/components/LogoutButton.vue'
import { useSessionStore } from '@/session/store'

const route = useRoute()
const router = useRouter()
const slug = String(route.params.slug)
const me = computed(() => useSessionStore().me[slug])

// 件名・本文・コメントの全文検索（F-V-4）
const q = ref('')
watch(
  () => route.query.q,
  (value) => (q.value = typeof value === 'string' ? value : ''),
  { immediate: true },
)
function search() {
  const text = q.value.trim()
  if (text) void router.push({ path: `/t/${slug}/agent/search`, query: { q: text } })
}
</script>

<template>
  <div class="agent">
    <header class="bar">
      <nav>
        <RouterLink
          :to="`/t/${slug}/agent`"
          :class="{ current: route.path.startsWith(`/t/${slug}/agent/views`) }"
        >
          チケット
        </RouterLink>
        <RouterLink :to="`/t/${slug}/agent/tickets/new`">
          新規チケット
        </RouterLink>
      </nav>
      <form
        role="search"
        @submit.prevent="search"
      >
        <InputText
          v-model="q"
          type="search"
          placeholder="チケットを検索"
          aria-label="チケットを検索"
          size="small"
        />
      </form>
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
nav a.router-link-exact-active,
nav a.current {
  color: var(--p-primary-color);
}
.me {
  color: var(--p-text-muted-color);
}
.content {
  padding: 1.5rem;
}
</style>
