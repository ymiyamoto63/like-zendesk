<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FormField from '@/features/auth/components/FormField.vue'
import { errorMessage } from '@/features/auth/messages'
import PortalLayout from '@/layouts/PortalLayout.vue'
import type { Attachment } from '@/shared/attachments'
import AttachmentPicker from '@/shared/components/AttachmentPicker.vue'
import { useSubmitRequest } from '../queries'

const route = useRoute()
const router = useRouter()
const slug = String(route.params.slug)
const submit = useSubmitRequest(slug)
const subject = ref('')
const body = ref('')
const attachments = ref<Attachment[]>([])
const error = ref('')

async function send() {
  error.value = ''
  try {
    const ticket = await submit.mutateAsync({
      subject: subject.value,
      body: body.value,
      attachmentIds: attachments.value.map((a) => a.id),
    })
    await router.push({ path: `/t/${slug}/requests/${ticket.number}`, query: { submitted: '1' } })
  } catch (e) {
    error.value = errorMessage(e)
  }
}
</script>

<template>
  <PortalLayout>
    <h1>新しいお問い合わせ</h1>
    <form
      class="form"
      @submit.prevent="send"
    >
      <Message
        v-if="error"
        severity="error"
      >
        {{ error }}
      </Message>
      <FormField
        id="subject"
        label="件名"
      >
        <InputText
          id="subject"
          v-model="subject"
          maxlength="250"
          placeholder="例: VPN に接続できない"
          required
        />
      </FormField>
      <FormField
        id="body"
        label="内容"
      >
        <Textarea
          id="body"
          v-model="body"
          rows="8"
          auto-resize
          placeholder="いつから、何をしたときに、どうなるかを書いてください。"
          required
        />
      </FormField>
      <AttachmentPicker
        v-model="attachments"
        :slug="slug"
        class="spaced"
      />
      <Button
        type="submit"
        label="送信する"
        :loading="submit.isPending.value"
      />
    </form>
  </PortalLayout>
</template>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 1.25rem;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.form :deep(button[type='submit']) {
  align-self: flex-start;
}
.spaced {
  margin-bottom: 1rem;
}
</style>
