<script setup lang="ts">
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import MultiSelect from 'primevue/multiselect'
import Select from 'primevue/select'
import { computed } from 'vue'
import type { Group } from '@/features/tickets/queries'
import {
  enumChoices,
  fieldLabels,
  ME,
  MY_GROUPS,
  newCondition,
  opLabels,
  opsByField,
  valueKind,
  withField,
  withOp,
  type Condition,
  type Field,
} from '../conditions'

const props = defineProps<{ groups: Group[] }>()
const conditions = defineModel<Condition[]>({ required: true })

const fields = (Object.keys(fieldLabels) as Field[]).map((value) => ({ value, label: fieldLabels[value] }))

const groupChoices = computed(() => [
  { value: MY_GROUPS, label: '自分のグループ' },
  ...props.groups.map((g) => ({ value: g.id, label: g.name })),
])

// 担当者に選べるのは、いずれかのグループのメンバー
const userChoices = computed(() => {
  const members = new Map<string, string>()
  for (const g of props.groups) for (const m of g.members) members.set(m.id, m.name)
  return [{ value: ME, label: '自分' }, ...[...members].map(([value, label]) => ({ value, label }))]
})

function opChoices(field: Field) {
  return opsByField[field].map((op) => ({ value: op, label: opLabels[op] }))
}

function choices(c: Condition) {
  return c.field === 'group' ? groupChoices.value : enumChoices(c.field)
}

function replace(index: number, next: Condition) {
  conditions.value = conditions.value.map((c, i) => (i === index ? next : c))
}

function tagsText(c: Condition): string {
  return Array.isArray(c.value) ? c.value.join(', ') : ''
}

function parseTags(text: string): string[] {
  return text
    .split(/[,、\s]+/)
    .map((t) => t.trim())
    .filter((t) => t !== '')
}
</script>

<template>
  <div class="conditions">
    <p
      v-if="conditions.length === 0"
      class="empty"
    >
      条件がないので、すべてのチケットが入ります。
    </p>
    <div
      v-for="(condition, index) in conditions"
      :key="index"
      class="row"
      :data-testid="`condition-${index}`"
    >
      <Select
        :model-value="condition.field"
        :options="fields"
        option-label="label"
        option-value="value"
        aria-label="項目"
        class="field"
        @update:model-value="replace(index, withField(condition, $event))"
      />
      <Select
        :model-value="condition.op"
        :options="opChoices(condition.field)"
        option-label="label"
        option-value="value"
        aria-label="条件"
        class="op"
        @update:model-value="replace(index, withOp(condition, $event))"
      />
      <MultiSelect
        v-if="valueKind(condition.op) === 'choices'"
        :model-value="condition.value as string[]"
        :options="choices(condition)"
        option-label="label"
        option-value="value"
        placeholder="選択"
        aria-label="値"
        display="chip"
        class="value"
        @update:model-value="replace(index, { ...condition, value: $event })"
      />
      <Select
        v-else-if="valueKind(condition.op) === 'user'"
        :model-value="condition.value as string"
        :options="userChoices"
        option-label="label"
        option-value="value"
        aria-label="値"
        class="value"
        @update:model-value="replace(index, { ...condition, value: $event })"
      />
      <InputText
        v-else-if="valueKind(condition.op) === 'tags'"
        :model-value="tagsText(condition)"
        placeholder="vpn, mail"
        aria-label="タグ"
        class="value"
        @update:model-value="replace(index, { ...condition, value: parseTags($event ?? '') })"
      />
      <InputNumber
        v-else-if="valueKind(condition.op) === 'days'"
        :model-value="condition.value as number"
        :min="1"
        :max="3650"
        suffix=" 日"
        aria-label="日数"
        class="value"
        @update:model-value="replace(index, { ...condition, value: $event })"
      />
      <span
        v-else
        class="value"
      />
      <Button
        label="削除"
        :aria-label="`条件 ${index + 1} を削除`"
        severity="secondary"
        text
        size="small"
        @click="conditions = conditions.filter((_, i) => i !== index)"
      />
    </div>
    <Button
      label="＋ 条件を追加"
      severity="secondary"
      size="small"
      outlined
      class="add"
      @click="conditions = [...conditions, newCondition()]"
    />
  </div>
</template>

<style scoped>
.conditions {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.row {
  display: grid;
  grid-template-columns: 9rem 12rem minmax(12rem, 1fr) auto;
  gap: 0.5rem;
  align-items: center;
}
.add {
  align-self: flex-start;
}
.empty {
  margin: 0;
  color: var(--p-text-muted-color);
}
</style>
