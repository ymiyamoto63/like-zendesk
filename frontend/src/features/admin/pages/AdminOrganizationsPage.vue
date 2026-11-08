<script setup lang="ts">
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import { reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AdminFrame from '../components/AdminFrame.vue'
import { adminErrorMessage } from '../messages'
import { useOrganizationMutations, useOrganizations } from '../queries'

// 依頼者の所属組織（F-A-4）。依頼者への割り当てはユーザー画面で行う。
const slug = String(useRoute().params.slug)
const organizations = useOrganizations(slug)
const { create, rename, remove } = useOrganizationMutations(slug)

const newName = ref('')
const error = ref('')
const notice = ref('')
const names = reactive<Record<string, string>>({})

watch(
  () => organizations.data.value,
  (all) => {
    for (const o of all ?? []) names[o.id] = o.name
  },
  { immediate: true },
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
  }, `組織「${name}」を作りました。`)
}

function save(id: string) {
  const name = (names[id] ?? '').trim()
  void run(() => rename.mutateAsync({ id, name }), `組織「${name}」を保存しました。`)
}

function destroy(id: string, name: string, members: number) {
  const note = members > 0 ? `所属している依頼者 ${members} 人は「所属なし」になります。` : ''
  if (!window.confirm(`組織「${name}」を削除しますか？${note}`)) return
  void run(() => remove.mutateAsync(id), `組織「${name}」を削除しました。`)
}
</script>

<template>
  <AdminFrame title="所属組織">
    <p class="lead">
      依頼者を組織にまとめると、ビューの条件「依頼者の組織」でチケットを絞り込めます。依頼者の所属はユーザー画面で設定します。
    </p>
    <form
      class="create"
      @submit.prevent="add"
    >
      <InputText
        v-model="newName"
        placeholder="新しい組織の名前"
        aria-label="新しい組織の名前"
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
      v-if="organizations.data.value?.length === 0"
      class="empty"
    >
      組織はまだありません。
    </p>
    <ul class="organizations">
      <li
        v-for="org in organizations.data.value ?? []"
        :key="org.id"
      >
        <InputText
          v-model="names[org.id]"
          :aria-label="`${org.name} の名前`"
          maxlength="100"
        />
        <span class="members">{{ org.members }} 人</span>
        <Button
          label="保存"
          size="small"
          :aria-label="`${org.name} を保存`"
          :disabled="(names[org.id] ?? '').trim() === org.name"
          @click="save(org.id)"
        />
        <Button
          label="削除"
          size="small"
          severity="danger"
          text
          :aria-label="`${org.name} を削除`"
          @click="destroy(org.id, org.name, org.members)"
        />
      </li>
    </ul>
  </AdminFrame>
</template>

<style scoped>
.lead,
.empty {
  color: var(--p-text-muted-color);
  margin-top: 0;
}
.create {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.spaced {
  margin-bottom: 1rem;
}
.organizations {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-width: 40rem;
}
.organizations li {
  display: grid;
  grid-template-columns: 1fr 4rem auto auto;
  gap: 0.5rem;
  align-items: center;
}
.members {
  color: var(--p-text-muted-color);
  text-align: right;
}
</style>
