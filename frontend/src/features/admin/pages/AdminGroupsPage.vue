<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import MultiSelect from 'primevue/multiselect'
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useGroups } from '@/features/tickets/queries'
import AdminFrame from '../components/AdminFrame.vue'
import { adminErrorMessage } from '../messages'
import { useActiveStaff, useGroupMutations } from '../queries'

// グループの作成・名前の変更・削除・メンバーの設定（F-A-3）
const slug = String(useRoute().params.slug)
const groups = useGroups(slug)
const staff = useActiveStaff(slug)
const { create, rename, remove, setMembers } = useGroupMutations(slug)

const newName = ref('')
const error = ref('')
const notice = ref('')
// グループごとの編集中の値
const drafts = reactive<Record<string, { name: string; members: string[] }>>({})

watch(
  () => groups.data.value,
  (all) => {
    for (const g of all ?? []) {
      drafts[g.id] = { name: g.name, members: g.members.map((m) => m.id) }
    }
  },
  { immediate: true },
)

const staffChoices = computed(() =>
  (staff.data.value ?? []).map((u) => ({ value: u.id, label: `${u.name}（${u.email}）` })),
)

async function run(action: () => Promise<unknown>, done: string) {
  error.value = ''
  notice.value = ''
  try {
    await action()
    notice.value = done
  } catch (e) {
    error.value = adminErrorMessage(e)
  }
}

function add() {
  const name = newName.value.trim()
  if (!name) return
  void run(async () => {
    await create.mutateAsync(name)
    newName.value = ''
  }, `グループ「${name}」を作りました。`)
}

function save(id: string, originalName: string) {
  const draft = drafts[id]
  void run(async () => {
    if (draft.name.trim() !== originalName) await rename.mutateAsync({ id, name: draft.name.trim() })
    await setMembers.mutateAsync({ id, userIds: draft.members })
  }, `グループ「${draft.name.trim()}」を保存しました。`)
}

function destroy(id: string, name: string) {
  if (!window.confirm(`グループ「${name}」を削除しますか？`)) return
  void run(() => remove.mutateAsync(id), `グループ「${name}」を削除しました。`)
}
</script>

<template>
  <AdminFrame title="グループ">
    <form
      class="create"
      @submit.prevent="add"
    >
      <InputText
        v-model="newName"
        placeholder="新しいグループの名前"
        aria-label="新しいグループの名前"
        maxlength="100"
      />
      <Button
        type="submit"
        label="追加する"
        :loading="create.isPending.value"
      />
    </form>
    <Message
      v-if="error"
      severity="error"
      class="spaced"
    >
      {{ error }}
    </Message>
    <Message
      v-if="notice"
      severity="success"
      class="spaced"
    >
      {{ notice }}
    </Message>
    <p
      v-if="groups.data.value?.length === 0"
      class="empty"
    >
      グループはまだありません。
    </p>
    <ul class="groups">
      <li
        v-for="group in groups.data.value ?? []"
        :key="group.id"
        :data-testid="`group-${group.name}`"
      >
        <template v-if="drafts[group.id]">
          <InputText
            v-model="drafts[group.id].name"
            :aria-label="`${group.name} の名前`"
            maxlength="100"
          />
          <MultiSelect
            v-model="drafts[group.id].members"
            :options="staffChoices"
            option-label="label"
            option-value="value"
            placeholder="メンバーを選ぶ"
            filter
            display="chip"
            :aria-label="`${group.name} のメンバー`"
            class="members"
          />
          <Button
            label="保存"
            size="small"
            :aria-label="`${group.name} を保存`"
            @click="save(group.id, group.name)"
          />
          <Button
            label="削除"
            size="small"
            severity="danger"
            text
            :aria-label="`${group.name} を削除`"
            @click="destroy(group.id, group.name)"
          />
        </template>
      </li>
    </ul>
  </AdminFrame>
</template>

<style scoped>
.create {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.spaced {
  margin-bottom: 1rem;
}
.empty {
  color: var(--p-text-muted-color);
}
.groups {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.groups li {
  display: grid;
  grid-template-columns: 14rem 1fr auto auto;
  gap: 0.5rem;
  align-items: center;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--p-content-border-color);
}
.members {
  min-width: 0;
}
</style>
