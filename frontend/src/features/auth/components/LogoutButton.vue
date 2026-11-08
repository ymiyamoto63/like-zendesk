<script setup lang="ts">
import Button from 'primevue/button'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/session/store'

const props = defineProps<{ slug: string }>()
const router = useRouter()
const session = useSessionStore()

async function logout() {
  await session.logout(props.slug)
  await router.push(`/t/${props.slug}/login`)
}
</script>

<template>
  <Button
    label="ログアウト"
    severity="secondary"
    size="small"
    @click="logout"
  />
</template>
