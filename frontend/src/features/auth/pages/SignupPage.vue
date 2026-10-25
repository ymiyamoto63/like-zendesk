<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PublicLayout from '@/layouts/PublicLayout.vue'
import { useSessionStore, type SignupRequest } from '@/session/store'
import FormField from '../components/FormField.vue'
import { errorMessage } from '../messages'

const router = useRouter()
const session = useSessionStore()

const form = reactive<SignupRequest>({ tenantName: '', slug: '', adminName: '', email: '', password: '' })
const error = ref('')
const submitting = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await session.signup(form)
    await router.push(`/t/${form.slug}/agent`)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <PublicLayout title="テナントを作成">
    <form @submit.prevent="submit">
      <Message
        v-if="error"
        severity="error"
        class="error"
      >
        {{ error }}
      </Message>
      <FormField
        id="tenant-name"
        label="テナント名"
        hint="会社名やチーム名など"
      >
        <InputText
          id="tenant-name"
          v-model="form.tenantName"
          maxlength="100"
          required
        />
      </FormField>
      <FormField
        id="slug"
        label="テナント識別子"
        :hint="`URL に使います（/t/${form.slug || 'acme'}）。英小文字・数字・ハイフンの 3〜32 文字`"
      >
        <InputText
          id="slug"
          v-model="form.slug"
          pattern="[a-z0-9][a-z0-9\-]{1,30}[a-z0-9]"
          required
        />
      </FormField>
      <FormField
        id="admin-name"
        label="あなたの名前"
      >
        <InputText
          id="admin-name"
          v-model="form.adminName"
          maxlength="100"
          required
        />
      </FormField>
      <FormField
        id="email"
        label="メールアドレス"
      >
        <InputText
          id="email"
          v-model="form.email"
          type="email"
          autocomplete="email"
          required
        />
      </FormField>
      <FormField
        id="password"
        label="パスワード"
        hint="8 文字以上"
      >
        <Password
          v-model="form.password"
          input-id="password"
          :feedback="false"
          autocomplete="new-password"
          toggle-mask
          fluid
          required
          :minlength="8"
        />
      </FormField>
      <Button
        type="submit"
        label="作成して始める"
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
