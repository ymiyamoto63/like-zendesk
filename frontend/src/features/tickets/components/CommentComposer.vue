<script setup lang="ts">
import Button from 'primevue/button'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import Textarea from 'primevue/textarea'
import { computed } from 'vue'
import type { Attachment } from '@/shared/attachments'
import AttachmentPicker from '@/shared/components/AttachmentPicker.vue'
import { selectableStatuses, type Status } from '@/shared/labels'

defineProps<{ slug: string; submitting: boolean; disabled?: boolean }>()
const body = defineModel<string>('body', { required: true })
const isPublic = defineModel<boolean>('isPublic', { required: true })
const status = defineModel<Status>('status', { required: true })
const attachments = defineModel<Attachment[]>('attachments', { required: true })
const emit = defineEmits<{ submit: [] }>()

const kinds = [
  { value: true, label: '公開返信' },
  { value: false, label: '社内メモ' },
]
const canSubmit = computed(() => body.value.trim().length > 0)
</script>

<template>
  <form
    class="composer"
    :class="{ internal: !isPublic }"
    @submit.prevent="canSubmit && emit('submit')"
  >
    <SelectButton
      v-model="isPublic"
      :options="kinds"
      option-label="label"
      option-value="value"
      :allow-empty="false"
      :disabled="disabled"
      aria-label="コメントの種類"
    />
    <Textarea
      id="comment-body"
      v-model="body"
      rows="5"
      auto-resize
      :disabled="disabled"
      :placeholder="isPublic ? '依頼者への返信（依頼者に送られます）' : '社内メモ（依頼者には見えません）'"
    />
    <AttachmentPicker
      v-model="attachments"
      :slug="slug"
      :disabled="disabled"
    />
    <div class="actions">
      <label>
        <span>送信して</span>
        <Select
          v-model="status"
          :options="selectableStatuses"
          option-label="label"
          option-value="value"
          :disabled="disabled"
          input-id="comment-status"
        />
      </label>
      <Button
        type="submit"
        :label="isPublic ? '返信を送信' : 'メモを追加'"
        :loading="submitting"
        :disabled="disabled || !canSubmit"
      />
    </div>
  </form>
</template>

<style scoped>
.composer {
  display: grid;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--p-content-border-color);
  border-radius: var(--p-content-border-radius);
  background: var(--p-content-background);
}
.composer.internal {
  background: var(--p-amber-50);
  border-color: var(--p-amber-300);
}
.actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 0.75rem;
}
.actions label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
</style>
