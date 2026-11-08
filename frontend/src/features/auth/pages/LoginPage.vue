<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { useSessionStore } from '@/session/store'
import FormField from '../components/FormField.vue'
import { errorMessage } from '../messages'
import { useTenant } from '../tenant'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { slug, tenant } = useTenant()

const email = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

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
  <PublicLayout :title="tenant ? `${tenant.name} にログイン` : 'ログイン'">
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
    <p class="links">
      <RouterLink :to="`/t/${slug}/password-reset`">
        パスワードを忘れた場合
      </RouterLink>
      <RouterLink :to="`/t/${slug}/register`">
        アカウントを登録する
      </RouterLink>
    </p>
  </PublicLayout>
</template>

<style scoped>
.error {
  margin-bottom: 1rem;
}
.links {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0;
}
</style>
