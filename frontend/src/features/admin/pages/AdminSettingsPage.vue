<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import RadioButton from 'primevue/radiobutton'
import Textarea from 'primevue/textarea'
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import FormField from '@/features/auth/components/FormField.vue'
import AdminFrame from '../components/AdminFrame.vue'
import { adminErrorMessage } from '../messages'
import { useSettings, useSettingsMutation, type Settings } from '../queries'

// テナント名と、依頼者の登録方法（F-X-6、F-A-2）
const slug = String(useRoute().params.slug)
const settings = useSettings(slug)
const save = useSettingsMutation(slug)

const name = ref('')
const signup = ref<Settings['requesterSignup']>('open')
const domains = ref('')
const error = ref('')
const notice = ref('')

watch(
  () => settings.data.value,
  (s) => {
    if (!s) return
    name.value = s.name
    signup.value = s.requesterSignup
    domains.value = s.allowedDomains.join('\n')
  },
  { immediate: true },
)

async function submit() {
  error.value = ''
  notice.value = ''
  const allowedDomains = domains.value
    .split(/[\s,、]+/)
    .map((d) => d.trim())
    .filter((d) => d !== '')
  if (signup.value === 'domain_restricted' && allowedDomains.length === 0) {
    error.value = '許可するドメインを 1 つ以上入力してください。'
    return
  }
  try {
    await save.mutateAsync({ name: name.value.trim(), requesterSignup: signup.value, allowedDomains })
    notice.value = '設定を保存しました。'
  } catch (e) {
    error.value = adminErrorMessage(e)
  }
}
</script>

<template>
  <AdminFrame title="テナント設定">
    <form
      v-if="settings.data.value"
      class="settings"
      @submit.prevent="submit"
    >
      <Message
        v-if="error"
        severity="error"
        class="spaced"
      >
        {{ error }}
      </Message>
      <Message
        v-if="notice"
        severity="success"
        class="spaced"
      >
        {{ notice }}
      </Message>
      <FormField
        id="tenant-name"
        label="テナント名"
        hint="メールの件名やポータルに表示されます。"
      >
        <InputText
          id="tenant-name"
          v-model="name"
          maxlength="100"
          required
        />
      </FormField>
      <fieldset>
        <legend>依頼者の登録</legend>
        <label>
          <RadioButton
            v-model="signup"
            input-id="signup-open"
            value="open"
          />
          誰でも登録できる
        </label>
        <label>
          <RadioButton
            v-model="signup"
            input-id="signup-domain"
            value="domain_restricted"
          />
          許可したドメインのメールアドレスだけ
        </label>
        <FormField
          v-if="signup === 'domain_restricted'"
          id="allowed-domains"
          label="許可するドメイン"
          hint="1 行に 1 つ（例: example.co.jp）"
        >
          <Textarea
            id="allowed-domains"
            v-model="domains"
            rows="4"
          />
        </FormField>
      </fieldset>
      <Button
        type="submit"
        label="保存する"
        :loading="save.isPending.value"
      />
    </form>
    <Message
      v-else-if="settings.isError.value"
      severity="error"
    >
      設定を読み込めませんでした。
    </Message>
  </AdminFrame>
</template>

<style scoped>
.settings {
  max-width: 36rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
}
.settings > :deep(.field),
fieldset {
  align-self: stretch;
}
.spaced {
  align-self: stretch;
}
fieldset {
  border: 1px solid var(--p-content-border-color);
  border-radius: 6px;
  padding: 0.75rem 1rem;
  margin: 0 0 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
legend {
  font-weight: 600;
  padding: 0 0.25rem;
}
fieldset label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
</style>
