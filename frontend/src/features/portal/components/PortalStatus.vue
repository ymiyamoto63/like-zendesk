<script setup lang="ts">
import Tag from 'primevue/tag'
import { computed } from 'vue'
import type { Status } from '@/shared/labels'
import { portalStatusLabels } from '../labels'

const props = defineProps<{ status: Status }>()

const severity = computed(() => {
  switch (props.status) {
    case 'pending':
      return 'warn'
    case 'solved':
    case 'closed':
      return 'success'
    default:
      return 'info'
  }
})
</script>

<template>
  <Tag
    :value="portalStatusLabels[status]"
    :severity="severity"
  />
</template>
