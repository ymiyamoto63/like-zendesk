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
import { useTenant } from '../tenant'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { slug, tenant } = useTenant()
const token = String(route.params.token)

const invitedEmail = ref('')
const name = ref('')
const password = ref('')
const error = ref('')
const invalid = ref(false)
const submitting = ref(false)

onMounted(async () => {
  try {
    const invitation = await unwrap(
      api.GET('/api/t/{slug}/invitations/{token}', { params: { path: { slug, token } } }),
    )
    invitedEmail.value = invitation.email
  } catch (e) {
    invalid.value = true
    error.value = errorMessage(e)
  }
})

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    const me = await unwrap(
      api.POST('/api/t/{slug}/invitations/{token}/accept', {
        params: { path: { slug, token } },
        body: { name: name.value, password: password.value },
      }),
    )
    session.loggedIn(slug, me)
    await router.push(`/t/${slug}/agent`)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <PublicLayout :title="tenant ? `${tenant.name} への招待` : '招待'">
    <Message
      v-if="error"
      severity="error"
      class="error"
    >
      {{ error }}
    </Message>
    <p v-if="invalid">
      管理者に招待を出し直してもらってください。
    </p>
    <form
      v-else-if="invitedEmail"
      @submit.prevent="submit"
    >
      <p>{{ invitedEmail }} として参加します。名前とパスワードを設定してください。</p>
      <FormField
        id="name"
        label="名前"
      >
        <InputText
          id="name"
          v-model="name"
          autocomplete="name"
          maxlength="100"
          required
        />
      </FormField>
      <FormField
        id="password"
        label="パスワード（8 文字以上）"
      >
        <Password
          v-model="password"
          input-id="password"
          :feedback="false"
          autocomplete="new-password"
          toggle-mask
          fluid
          required
          :minlength="8"
          :maxlength="72"
        />
      </FormField>
      <Button
        type="submit"
        label="参加する"
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
