<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import AgentLayout from '@/layouts/AgentLayout.vue'

defineProps<{ title: string }>()

const slug = String(useRoute().params.slug)
const tabs = [
  { path: 'users', label: 'ユーザー' },
  { path: 'groups', label: 'グループ' },
  { path: 'organizations', label: '所属組織' },
  { path: 'settings', label: 'テナント設定' },
]
</script>

<template>
  <AgentLayout>
    <div class="admin">
      <nav aria-label="管理メニュー">
        <RouterLink
          v-for="tab in tabs"
          :key="tab.path"
          :to="`/t/${slug}/agent/admin/${tab.path}`"
        >
          {{ tab.label }}
        </RouterLink>
      </nav>
      <section>
        <h1>{{ title }}</h1>
        <slot />
      </section>
    </div>
  </AgentLayout>
</template>

<style scoped>
.admin {
  display: grid;
  grid-template-columns: 12rem 1fr;
  gap: 1.5rem;
}
nav {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
nav a {
  padding: 0.375rem 0.75rem;
  border-radius: 6px;
  color: var(--p-text-color);
  text-decoration: none;
}
nav a.router-link-active {
  background: var(--p-highlight-background);
  color: var(--p-highlight-color);
  font-weight: 600;
}
section {
  min-width: 0;
}
h1 {
  margin-top: 0;
  font-size: 1.25rem;
}
</style>
