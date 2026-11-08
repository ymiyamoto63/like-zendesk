<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useViews, type View } from '../queries'

const props = defineProps<{ slug: string; active?: string }>()
const { data, isError } = useViews(props.slug)

const sections = computed(() => {
  const views = data.value ?? []
  const of = (kind: View['kind']) => views.filter((v) => v.kind === kind)
  return [
    { title: '標準', views: of('builtin') },
    { title: '共有', views: of('shared') },
    { title: '個人', views: of('personal') },
  ].filter((s) => s.views.length > 0)
})
</script>

<template>
  <nav
    class="views"
    aria-label="ビュー"
  >
    <p
      v-if="isError"
      class="error"
    >
      ビューを読み込めませんでした。
    </p>
    <section
      v-for="section in sections"
      :key="section.title"
    >
      <h2>{{ section.title }}</h2>
      <RouterLink
        v-for="view in section.views"
        :key="view.id"
        :to="`/t/${slug}/agent/views/${encodeURIComponent(view.id)}`"
        :class="{ active: view.id === active }"
        :aria-current="view.id === active ? 'page' : undefined"
      >
        <span class="name">{{ view.name }}</span>
        <span
          v-if="view.count !== null"
          class="count"
        >{{ view.count }}</span>
      </RouterLink>
    </section>
    <RouterLink
      :to="`/t/${slug}/agent/views/new`"
      class="new"
    >
      ＋ ビューを作成
    </RouterLink>
  </nav>
</template>

<style scoped>
.views {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 13rem;
}
h2 {
  margin: 0 0 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--p-text-muted-color);
}
a {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.3rem 0.5rem;
  border-radius: 6px;
  color: var(--p-text-color);
  text-decoration: none;
}
a:hover {
  background: var(--p-content-hover-background);
}
a.active {
  background: var(--p-highlight-background);
  color: var(--p-highlight-color);
  font-weight: 600;
}
.count {
  color: var(--p-text-muted-color);
  font-variant-numeric: tabular-nums;
}
.new {
  color: var(--p-primary-color);
}
.error {
  color: var(--p-red-500);
}
</style>
