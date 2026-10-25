<script setup lang="ts">
import { onMounted, ref } from 'vue'

type State = 'checking' | 'up' | 'down'

const state = ref<State>('checking')

const labels: Record<State, string> = {
  checking: '確認中…',
  up: '稼働中',
  down: '応答なし',
}

onMounted(async () => {
  try {
    const res = await fetch('/api/actuator/health')
    const body: { status?: string } = res.ok ? await res.json() : {}
    state.value = body.status === 'UP' ? 'up' : 'down'
  } catch {
    state.value = 'down'
  }
})
</script>

<template>
  <p>API: <span :data-state="state">{{ labels[state] }}</span></p>
</template>
