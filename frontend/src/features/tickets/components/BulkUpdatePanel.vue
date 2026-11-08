<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import { computed, ref, watch } from 'vue'
import { statusLabels, options } from '@/shared/labels'
import { KEEP, NONE, bulkRequest, bulkSummary, emptyBulkForm, type BulkForm } from '../bulk'
import { ticketErrorMessage } from '../messages'
import { useBulkUpdate, type Group } from '../queries'

// 一覧で選んだチケットをまとめて更新する（F-T-8）
const props = defineProps<{ slug: string; numbers: number[]; groups: Group[] }>()
const emit = defineEmits<{ done: [{ severity: 'success' | 'warn'; text: string }]; cancel: [] }>()

const form = ref<BulkForm>(emptyBulkForm())
const error = ref('')
const bulk = useBulkUpdate(props.slug)

const keep = { value: KEEP, label: '変更しない' }
const statusChoices = [keep, ...options(statusLabels).filter((o) => o.value !== 'closed')]
const groupChoices = computed(() => [keep, ...props.groups.map((g) => ({ value: g.id, label: g.name }))])
// グループを選んだらそのメンバー、選ばなければ全グループのメンバー（チケットのグループに属さなければ失敗になる）
const assigneeChoices = computed(() => {
  const members = new Map<string, string>()
  for (const g of props.groups) {
    if (form.value.groupId === KEEP || g.id === form.value.groupId) {
      for (const m of g.members) members.set(m.id, m.name)
    }
  }
  return [keep, { value: NONE, label: '担当者を外す' }, ...[...members].map(([value, label]) => ({ value, label }))]
})
watch(
  () => form.value.groupId,
  () => {
    if (!assigneeChoices.value.some((c) => c.value === form.value.assigneeId)) form.value.assigneeId = KEEP
  },
)

const request = computed(() => bulkRequest(props.numbers, form.value))

async function apply() {
  if (!request.value) return
  error.value = ''
  try {
    const result = await bulk.mutateAsync(request.value)
    emit('done', { severity: result.failed.length ? 'warn' : 'success', text: bulkSummary(result) })
  } catch (e) {
    error.value = ticketErrorMessage(e)
  }
}
</script>

<template>
  <section
    class="bulk"
    aria-label="一括更新"
  >
    <strong>{{ numbers.length }} 件を選択中</strong>
    <label>
      <span>状態</span>
      <Select
        v-model="form.status"
        :options="statusChoices"
        option-label="label"
        option-value="value"
        input-id="bulk-status"
        size="small"
      />
    </label>
    <label>
      <span>グループ</span>
      <Select
        v-model="form.groupId"
        :options="groupChoices"
        option-label="label"
        option-value="value"
        input-id="bulk-group"
        size="small"
      />
    </label>
    <label>
      <span>担当者</span>
      <Select
        v-model="form.assigneeId"
        :options="assigneeChoices"
        option-label="label"
        option-value="value"
        input-id="bulk-assignee"
        size="small"
      />
    </label>
    <label>
      <span>タグを追加</span>
      <InputText
        id="bulk-add-tags"
        v-model="form.addTags"
        placeholder="vpn, 至急"
        size="small"
      />
    </label>
    <label>
      <span>タグを削除</span>
      <InputText
        id="bulk-remove-tags"
        v-model="form.removeTags"
        size="small"
      />
    </label>
    <div class="actions">
      <Button
        label="まとめて更新"
        size="small"
        :disabled="!request"
        :loading="bulk.isPending.value"
        @click="apply"
      />
      <Button
        label="選択を解除"
        size="small"
        severity="secondary"
        text
        @click="emit('cancel')"
      />
    </div>
    <p
      v-if="error"
      class="error"
      role="alert"
    >
      {{ error }}
    </p>
  </section>
</template>

<style scoped>
.bulk {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  margin-bottom: 0.75rem;
  border: 1px solid var(--p-primary-200);
  border-radius: var(--p-content-border-radius);
  background: var(--p-primary-50);
}
strong {
  align-self: center;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.875rem;
}
label :deep(.p-select),
label input {
  min-width: 9rem;
}
.actions {
  display: flex;
  gap: 0.25rem;
}
.error {
  flex-basis: 100%;
  margin: 0;
  color: var(--p-red-600);
}
</style>
