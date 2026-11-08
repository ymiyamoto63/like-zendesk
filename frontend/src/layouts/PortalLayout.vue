<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import LogoutButton from '@/features/auth/components/LogoutButton.vue'
import { useTenant } from '@/features/auth/tenant'
import { useSessionStore } from '@/session/store'

const slug = String(useRoute().params.slug)
const { tenant } = useTenant()
const me = computed(() => useSessionStore().me[slug])
</script>

<template>
  <div class="portal">
    <header class="bar">
      <RouterLink
        :to="`/t/${slug}`"
        class="brand"
        :aria-label="tenant?.name ?? 'ホーム'"
      >
        {{ tenant?.name ?? '' }}
      </RouterLink>
      <span class="me">{{ me?.name }}</span>
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
  gap: 0.75rem;
  padding: 0.5rem 1rem;
  background: var(--p-content-background);
  border-bottom: 1px solid var(--p-content-border-color);
}
.brand {
  margin-right: auto;
  font-weight: 700;
  color: var(--p-text-color);
  text-decoration: none;
}
.me {
  color: var(--p-text-muted-color);
}
.content {
  max-width: 48rem;
  margin: 0 auto;
  padding: 1.25rem 1rem 3rem;
}
@media (max-width: 480px) {
  .me {
    display: none;
  }
}
</style>
