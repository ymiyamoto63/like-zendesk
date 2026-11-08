<script setup lang="ts">
import Tag from 'primevue/tag'
import { computed } from 'vue'
import { statusLabels, type Status } from '@/shared/labels'

const props = defineProps<{ status: Status }>()

const severity = computed(() => {
  switch (props.status) {
    case 'new':
      return 'warn'
    case 'open':
      return 'danger'
    case 'pending':
      return 'info'
    case 'on_hold':
      return 'contrast'
    case 'solved':
      return 'success'
    case 'closed':
      return 'secondary'
  }
  return 'secondary'
})
</script>

<template>
  <Tag
    :value="statusLabels[status]"
    :severity="severity"
  />
</template>
