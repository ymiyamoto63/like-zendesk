<script setup lang="ts">
defineProps<{ id: string; label: string; hint?: string }>()
</script>

<template>
  <div class="field">
    <label :for="id">{{ label }}</label>
    <slot />
    <small v-if="hint">{{ hint }}</small>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  margin-bottom: 1rem;
}
small {
  color: var(--p-text-muted-color);
}
</style>
