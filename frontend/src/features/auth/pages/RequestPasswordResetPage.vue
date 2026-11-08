<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import { ref } from 'vue'
import { api, unwrap } from '@/api/client'
import PublicLayout from '@/layouts/PublicLayout.vue'
import FormField from '../components/FormField.vue'
import { errorMessage } from '../messages'
import { useTenant } from '../tenant'

const { slug } = useTenant()

const email = ref('')
const error = ref('')
const submitting = ref(false)
const sent = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await unwrap(
      api.POST('/api/t/{slug}/password-resets', { params: { path: { slug } }, body: { email: email.value } }),
    )
    sent.value = true
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <PublicLayout title="パスワードの再設定">
    <p v-if="sent">
      {{ email }} が登録済みなら、再設定のメールを送りました。リンクの有効期限は 1 時間です。
    </p>
    <form
      v-else
      @submit.prevent="submit"
    >
      <Message
        v-if="error"
        severity="error"
        class="error"
      >
        {{ error }}
      </Message>
      <p>登録したメールアドレスに、再設定のリンクを送ります。</p>
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
      <Button
        type="submit"
        label="メールを送る"
        :loading="submitting"
        fluid
      />
    </form>
    <p class="links">
      <RouterLink :to="`/t/${slug}/login`">
        ログイン画面に戻る
      </RouterLink>
    </p>
  </PublicLayout>
</template>

<style scoped>
.error {
  margin-bottom: 1rem;
}
.links {
  margin-bottom: 0;
  text-align: center;
}
</style>
