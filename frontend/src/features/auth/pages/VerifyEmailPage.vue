<script setup lang="ts">
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, unwrap } from '@/api/client'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { useSessionStore } from '@/session/store'
import { errorMessage } from '../messages'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const slug = String(route.params.slug)
const error = ref('')

onMounted(async () => {
  try {
    const me = await unwrap(
      api.POST('/api/t/{slug}/email-verifications', {
        params: { path: { slug } },
        body: { token: String(route.params.token) },
      }),
    )
    session.loggedIn(slug, me)
    await router.replace(`/t/${slug}`)
  } catch (e) {
    error.value = errorMessage(e)
  }
})
</script>

<template>
  <PublicLayout title="メールアドレスの確認">
    <template v-if="error">
      <Message severity="error">
        {{ error }}
      </Message>
      <p>
        <RouterLink :to="`/t/${slug}/register`">
          もう一度登録する
        </RouterLink>
        か、確認が済んでいれば
        <RouterLink :to="`/t/${slug}/login`">
          ログイン
        </RouterLink>
        してください。
      </p>
    </template>
    <ProgressSpinner
      v-else
      aria-label="確認しています"
    />
  </PublicLayout>
</template>
