<script setup lang="ts">
import { attachmentUrl, formatSize, type Attachment } from '@/shared/attachments'

defineProps<{ slug: string; attachments: Attachment[] }>()
</script>

<template>
  <ul
    v-if="attachments.length > 0"
    class="attachments"
    aria-label="添付ファイル"
  >
    <li
      v-for="file in attachments"
      :key="file.id"
    >
      <a
        :href="attachmentUrl(slug, file.id)"
        download
      >
        {{ file.filename }}
      </a>
      <span class="size">{{ formatSize(file.size) }}</span>
    </li>
  </ul>
</template>

<style scoped>
.attachments {
  list-style: none;
  padding: 0;
  margin: 0.5rem 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1rem;
}
a {
  color: var(--p-primary-color);
  text-decoration: none;
}
a:hover {
  text-decoration: underline;
}
.size {
  margin-left: 0.25rem;
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}
</style>
