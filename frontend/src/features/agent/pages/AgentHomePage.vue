<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import LogoutButton from '@/features/auth/components/LogoutButton.vue'
import { useSessionStore } from '@/session/store'

const slug = String(useRoute().params.slug)
const me = computed(() => useSessionStore().me[slug])
</script>

<template>
  <main class="page">
    <header>
      <h1>エージェント画面</h1>
      <span>{{ me?.name }}（{{ me?.role === 'admin' ? '管理者' : 'エージェント' }}）</span>
      <LogoutButton :slug="slug" />
    </header>
    <p>チケットの一覧と詳細は次の段階で作ります。</p>
  </main>
</template>

<style scoped>
.page {
  padding: 1.5rem;
}
header {
  display: flex;
  align-items: center;
  gap: 1rem;
}
h1 {
  margin: 0 auto 0 0;
  font-size: 1.25rem;
}
</style>
