<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, unwrap } from '@/api/client'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { useSessionStore } from '@/session/store'
import FormField from '../components/FormField.vue'
import { errorMessage } from '../messages'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const slug = String(route.params.slug)

const tenantName = ref('')
const email = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

onMounted(async () => {
  try {
    const tenant = await unwrap(api.GET('/api/t/{slug}', { params: { path: { slug } } }))
    tenantName.value = tenant.name
  } catch {
    await router.replace({ name: 'not-found', params: { pathMatch: route.path.slice(1).split('/') } })
  }
})

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    const me = await session.login(slug, email.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : undefined
    const home = me.role === 'requester' ? `/t/${slug}` : `/t/${slug}/agent`
    // 他のテナントや外部の URL には戻さない
    const sameTenant = redirect === `/t/${slug}` || redirect?.startsWith(`/t/${slug}/`)
    await router.push(sameTenant && redirect ? redirect : home)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <PublicLayout :title="tenantName ? `${tenantName} にログイン` : 'ログイン'">
    <form @submit.prevent="submit">
      <Message
        v-if="error"
        severity="error"
        class="error"
      >
        {{ error }}
      </Message>
      <FormField
        id="email"
        label="メールアドレス"
      >
        <InputText
          id="email"
          v-model="email"
          type="email"
          autocomplete="username"
          required
        />
      </FormField>
      <FormField
        id="password"
        label="パスワード"
      >
        <Password
          v-model="password"
          input-id="password"
          :feedback="false"
          autocomplete="current-password"
          toggle-mask
          fluid
          required
        />
      </FormField>
      <Button
        type="submit"
        label="ログイン"
        :loading="submitting"
        fluid
      />
    </form>
  </PublicLayout>
</template>

<style scoped>
.error {
  margin-bottom: 1rem;
}
</style>
