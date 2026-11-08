<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import { ref } from 'vue'
import { api, unwrap } from '@/api/client'
import PublicLayout from '@/layouts/PublicLayout.vue'
import FormField from '../components/FormField.vue'
import { errorMessage } from '../messages'
import { useTenant } from '../tenant'

const { slug, tenant } = useTenant()

const name = ref('')
const email = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)
const sent = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await unwrap(
      api.POST('/api/t/{slug}/requesters', {
        params: { path: { slug } },
        body: { name: name.value, email: email.value, password: password.value },
      }),
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
  <PublicLayout :title="tenant ? `${tenant.name} に登録` : '登録'">
    <template v-if="sent">
      <p>{{ email }} に確認メールを送りました。メールのリンクを開くと登録が完了します。</p>
      <p>リンクの有効期限は 24 時間です。</p>
    </template>
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
      <p
        v-if="tenant?.requesterSignup === 'domain_restricted'"
        class="hint"
      >
        所属先のメールアドレスで登録してください。
      </p>
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
        id="email"
        label="メールアドレス"
      >
        <InputText
          id="email"
          v-model="email"
          type="email"
          autocomplete="email"
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
        label="登録する"
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
.hint {
  margin-top: 0;
}
.links {
  margin-bottom: 0;
  text-align: center;
}
</style>
