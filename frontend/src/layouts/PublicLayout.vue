<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<template>
  <main class="public">
    <section class="panel">
      <h1>{{ title }}</h1>
      <slot />
    </section>
  </main>
</template>

<style scoped>
.public {
  display: flex;
  justify-content: center;
  padding: 4rem 1rem;
}
.panel {
  width: 100%;
  max-width: 26rem;
  padding: 2rem;
  background: var(--p-content-background);
  border: 1px solid var(--p-content-border-color);
  border-radius: var(--p-content-border-radius);
}
h1 {
  margin-top: 0;
  font-size: 1.5rem;
}
</style>
