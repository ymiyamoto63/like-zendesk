<script setup lang="ts">
import Button from 'primevue/button'
import Message from 'primevue/message'
import Password from 'primevue/password'
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, unwrap } from '@/api/client'
import PublicLayout from '@/layouts/PublicLayout.vue'
import FormField from '../components/FormField.vue'
import { errorMessage } from '../messages'
import { useTenant } from '../tenant'

const route = useRoute()
const { slug } = useTenant()
const token = String(route.params.token)

const password = ref('')
const error = ref('')
const submitting = ref(false)
const done = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await unwrap(
      api.PUT('/api/t/{slug}/password-resets/{token}', {
        params: { path: { slug, token } },
        body: { password: password.value },
      }),
    )
    done.value = true
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <PublicLayout title="新しいパスワード">
    <p v-if="done">
      パスワードを変更しました。
      <RouterLink :to="`/t/${slug}/login`">
        ログイン
      </RouterLink>
      してください。
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
      <FormField
        id="password"
        label="新しいパスワード（8 文字以上）"
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
        label="変更する"
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
