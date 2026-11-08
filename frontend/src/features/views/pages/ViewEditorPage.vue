<script setup lang="ts">
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FormField from '@/features/auth/components/FormField.vue'
import { useOrganizations } from '@/features/admin/queries'
import { useGroups } from '@/features/tickets/queries'
import AgentLayout from '@/layouts/AgentLayout.vue'
import { useSessionStore } from '@/session/store'
import ConditionEditor from '../components/ConditionEditor.vue'
import { problem, sortLabels, type Condition, type SortField } from '../conditions'
import { viewErrorMessage } from '../messages'
import { useViewMutations, useViews } from '../queries'

// ビューの作成（/agent/views/new）と編集（/agent/views/:viewId/edit）。
const route = useRoute()
const router = useRouter()
const slug = String(route.params.slug)
const id = route.params.viewId === undefined ? null : String(route.params.viewId)
const isAdmin = computed(() => useSessionStore().me[slug]?.role === 'admin')
const groups = useGroups(slug)
const organizations = useOrganizations(slug)
const views = useViews(slug)
const { create, update, remove } = useViewMutations(slug)

const name = ref('')
const shared = ref(false)
const conditions = ref<Condition[]>([])
const sortField = ref<SortField>('updated_at')
const descending = ref(true)
const error = ref('')
const loaded = ref(id === null)

const sortFields = (Object.keys(sortLabels) as SortField[]).map((value) => ({ value, label: sortLabels[value] }))
const directions = [
  { value: true, label: '降順（新しい・高い順）' },
  { value: false, label: '昇順' },
]

// 編集では、読み込んだビューで一度だけ入力欄を埋める
watch(
  () => views.data.value,
  (all) => {
    if (loaded.value || !all) return
    const view = all.find((v) => v.id === id)
    if (!view) {
      error.value = 'このビューは見つかりません。'
      return
    }
    name.value = view.name
    shared.value = view.kind === 'shared'
    conditions.value = view.conditions.all.map((c) => ({ ...c }))
    sortField.value = view.sort[0]?.field ?? 'updated_at'
    descending.value = (view.sort[0]?.direction ?? 'desc') === 'desc'
    loaded.value = true
  },
  { immediate: true },
)

const saving = computed(() => create.isPending.value || update.isPending.value)

async function save() {
  error.value = problem(conditions.value) ?? ''
  if (error.value) return
  const body = {
    name: name.value,
    conditions: { all: conditions.value },
    sort: [{ field: sortField.value, direction: descending.value ? ('desc' as const) : ('asc' as const) }],
  }
  try {
    const saved = id
      ? await update.mutateAsync({ id, body })
      : await create.mutateAsync({ ...body, shared: shared.value })
    await router.push(`/t/${slug}/agent/views/${encodeURIComponent(saved.id)}`)
  } catch (e) {
    error.value = viewErrorMessage(e)
  }
}

async function destroy() {
  if (!id || !window.confirm(`ビュー「${name.value}」を削除しますか？`)) return
  try {
    await remove.mutateAsync(id)
    await router.push(`/t/${slug}/agent`)
  } catch (e) {
    error.value = viewErrorMessage(e)
  }
}
</script>

<template>
  <AgentLayout>
    <h1>{{ id ? 'ビューを編集' : 'ビューを作成' }}</h1>
    <form
      v-if="loaded"
      class="editor"
      @submit.prevent="save"
    >
      <Message
        v-if="error"
        severity="error"
      >
        {{ error }}
      </Message>
      <FormField
        id="view-name"
        label="名前"
      >
        <InputText
          id="view-name"
          v-model="name"
          maxlength="100"
          required
        />
      </FormField>
      <label
        v-if="isAdmin && !id"
        class="shared"
      >
        <Checkbox
          v-model="shared"
          binary
          input-id="view-shared"
        />
        全エージェントで共有する
      </label>
      <fieldset>
        <legend>条件（すべてに一致）</legend>
        <ConditionEditor
          v-model="conditions"
          :groups="groups.data.value ?? []"
          :organizations="organizations.data.value ?? []"
        />
      </fieldset>
      <fieldset class="sort">
        <legend>並び順</legend>
        <Select
          v-model="sortField"
          :options="sortFields"
          option-label="label"
          option-value="value"
          aria-label="並べ替える項目"
        />
        <Select
          v-model="descending"
          :options="directions"
          option-label="label"
          option-value="value"
          aria-label="並べ替えの向き"
        />
      </fieldset>
      <div class="actions">
        <Button
          type="submit"
          label="保存する"
          :loading="saving"
        />
        <Button
          label="キャンセル"
          severity="secondary"
          text
          @click="router.back()"
        />
        <Button
          v-if="id"
          label="削除"
          severity="danger"
          text
          class="delete"
          :loading="remove.isPending.value"
          @click="destroy"
        />
      </div>
    </form>
    <Message
      v-else-if="error"
      severity="error"
    >
      {{ error }}
    </Message>
  </AgentLayout>
</template>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 1.25rem;
}
.editor {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 52rem;
}
fieldset {
  border: 1px solid var(--p-content-border-color);
  border-radius: 6px;
  padding: 0.75rem 1rem 1rem;
  margin: 0;
}
legend {
  padding: 0 0.25rem;
  font-weight: 600;
}
.sort {
  display: flex;
  gap: 0.5rem;
}
.shared {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
.delete {
  margin-left: auto;
}
</style>
