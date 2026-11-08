<script setup lang="ts">
import Button from 'primevue/button'
import { ref } from 'vue'
import { ApiError } from '@/api/client'
import {
  MAX_ATTACHMENT_BYTES,
  MAX_ATTACHMENTS,
  formatSize,
  uploadAttachment,
  type Attachment,
} from '@/shared/attachments'

// ファイルを選んだ時点でアップロードし、送信時は ID だけを送る（architecture.md 2.9）
const props = defineProps<{ slug: string; disabled?: boolean }>()
const files = defineModel<Attachment[]>({ required: true })
const uploading = ref(0)
const error = ref('')
const input = ref<HTMLInputElement | null>(null)

async function choose(event: Event) {
  const target = event.target as HTMLInputElement
  const chosen = [...(target.files ?? [])]
  target.value = ''
  error.value = ''
  for (const file of chosen) {
    if (files.value.length + uploading.value >= MAX_ATTACHMENTS) {
      error.value = `添付は ${MAX_ATTACHMENTS} 個までです。`
      break
    }
    if (file.size > MAX_ATTACHMENT_BYTES) {
      error.value = `「${file.name}」は 20MB を超えているため添付できません。`
      continue
    }
    if (file.size === 0) {
      error.value = `「${file.name}」は空のファイルです。`
      continue
    }
    uploading.value++
    try {
      const uploaded = await uploadAttachment(props.slug, file)
      files.value = [...files.value, uploaded]
    } catch (e) {
      error.value =
        e instanceof ApiError && e.code === 'attachment.too_large'
          ? `「${file.name}」は 20MB を超えているため添付できません。`
          : `「${file.name}」をアップロードできませんでした。`
    } finally {
      uploading.value--
    }
  }
}

function remove(id: string) {
  files.value = files.value.filter((f) => f.id !== id)
}
</script>

<template>
  <div class="picker">
    <input
      ref="input"
      type="file"
      multiple
      hidden
      data-testid="attachment-input"
      @change="choose"
    >
    <Button
      type="button"
      label="ファイルを添付"
      severity="secondary"
      size="small"
      text
      :loading="uploading > 0"
      :disabled="disabled"
      @click="input?.click()"
    />
    <ul
      v-if="files.length > 0"
      aria-label="添付するファイル"
    >
      <li
        v-for="file in files"
        :key="file.id"
      >
        {{ file.filename }}
        <span class="size">{{ formatSize(file.size) }}</span>
        <Button
          type="button"
          label="外す"
          size="small"
          text
          severity="secondary"
          :aria-label="`${file.filename} を外す`"
          @click="remove(file.id)"
        />
      </li>
    </ul>
    <small
      v-if="error"
      class="error"
      role="alert"
    >{{ error }}</small>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}
ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.75rem;
}
li {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}
.size {
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}
.error {
  flex-basis: 100%;
  color: var(--p-red-500);
}
</style>
