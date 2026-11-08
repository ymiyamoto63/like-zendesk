<script setup lang="ts">
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import { computed, ref, watch } from 'vue'
import { options, priorityLabels, selectableStatuses, statusLabels, typeLabels } from '@/shared/labels'
import { assigneeOptions, parseTags, withGroup, type TicketDraft } from '../draft'
import type { Group } from '../queries'

const props = defineProps<{ groups: Group[]; showStatus?: boolean; disabled?: boolean }>()
const draft = defineModel<TicketDraft>({ required: true })

// クローズは選べないが、クローズしたチケットでは表示はする
const statuses = computed(() => (draft.value.status === 'closed' ? options(statusLabels) : selectableStatuses))
const assignees = computed(() => assigneeOptions(props.groups, draft.value.groupId))
const tagsText = ref(draft.value.tags.join(', '))
watch(
  () => draft.value.tags,
  (tags) => {
    if (parseTags(tagsText.value).join() !== tags.join()) tagsText.value = tags.join(', ')
  },
)

function set<K extends keyof TicketDraft>(key: K, value: TicketDraft[K]) {
  draft.value = { ...draft.value, [key]: value }
}
</script>

<template>
  <div class="fields">
    <label v-if="showStatus">
      <span>状態</span>
      <Select
        :model-value="draft.status"
        :options="statuses"
        option-label="label"
        option-value="value"
        :disabled="disabled"
        input-id="field-status"
        @update:model-value="set('status', $event)"
      />
    </label>
    <label>
      <span>優先度</span>
      <Select
        :model-value="draft.priority"
        :options="options(priorityLabels)"
        option-label="label"
        option-value="value"
        :disabled="disabled"
        input-id="field-priority"
        @update:model-value="set('priority', $event)"
      />
    </label>
    <label>
      <span>種別</span>
      <Select
        :model-value="draft.type"
        :options="options(typeLabels)"
        option-label="label"
        option-value="value"
        :disabled="disabled"
        input-id="field-type"
        @update:model-value="set('type', $event)"
      />
    </label>
    <label>
      <span>グループ</span>
      <Select
        :model-value="draft.groupId"
        :options="groups"
        option-label="name"
        option-value="id"
        show-clear
        placeholder="なし"
        :disabled="disabled"
        input-id="field-group"
        @update:model-value="draft = withGroup(draft, groups, $event ?? null)"
      />
    </label>
    <label>
      <span>担当者</span>
      <Select
        :model-value="draft.assigneeId"
        :options="assignees"
        option-label="name"
        option-value="id"
        show-clear
        :placeholder="draft.groupId ? 'なし' : '先にグループを選ぶ'"
        :disabled="disabled || !draft.groupId"
        input-id="field-assignee"
        @update:model-value="set('assigneeId', $event ?? null)"
      />
    </label>
    <label>
      <span>タグ</span>
      <InputText
        id="field-tags"
        v-model="tagsText"
        placeholder="vpn, remote"
        :disabled="disabled"
        @change="set('tags', parseTags(tagsText))"
      />
    </label>
  </div>
</template>

<style scoped>
.fields {
  display: grid;
  gap: 0.75rem;
}
label {
  display: grid;
  gap: 0.25rem;
}
label > span {
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}
</style>
