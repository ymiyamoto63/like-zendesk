<script setup lang="ts">
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable, { type DataTablePageEvent } from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import FormField from '@/features/auth/components/FormField.vue'
import { useSessionStore } from '@/session/store'
import AdminFrame from '../components/AdminFrame.vue'
import { adminErrorMessage, roleLabels, userStatusLabels } from '../messages'
import {
  USERS_PAGE_SIZE,
  useAdminUsers,
  useOrganizations,
  useUserMutations,
  type AdminUser,
  type UserChanges,
  type UserQuery,
} from '../queries'

// ユーザーの一覧・招待・編集（F-A-1、F-A-4）
const slug = String(useRoute().params.slug)
const myId = computed(() => useSessionStore().me[slug]?.id)

const query = ref<UserQuery>({ role: 'all', status: 'all', q: '', page: 0 })
const search = ref('')
const { data, isPending, isError } = useAdminUsers(slug, query)
const organizations = useOrganizations(slug)
const { update, invite } = useUserMutations(slug)

const roleFilter = [{ value: 'all', label: 'すべてのロール' }, ...options(roleLabels)]
const statusFilter = [{ value: 'all', label: 'すべての状態' }, ...options(userStatusLabels)]

function options<K extends string>(labels: Record<K, string>) {
  return (Object.keys(labels) as K[]).map((value) => ({ value, label: labels[value] }))
}

watch(
  () => [query.value.role, query.value.status],
  () => (query.value.page = 0),
)

function applySearch() {
  query.value = { ...query.value, q: search.value.trim(), page: 0 }
}

function changePage(event: DataTablePageEvent) {
  query.value = { ...query.value, page: event.page }
}

// 招待（エージェント・管理者）
const inviteEmail = ref('')
const inviteRole = ref<'agent' | 'admin'>('agent')
const inviteMessage = ref<{ severity: 'success' | 'error'; text: string } | null>(null)

async function sendInvitation() {
  inviteMessage.value = null
  try {
    await invite.mutateAsync({ email: inviteEmail.value.trim(), role: inviteRole.value })
    inviteMessage.value = { severity: 'success', text: `${inviteEmail.value} に招待メールを送りました。` }
    inviteEmail.value = ''
  } catch (e) {
    inviteMessage.value = { severity: 'error', text: adminErrorMessage(e) }
  }
}

// 編集ダイアログ
const editing = ref<AdminUser | null>(null)
const editRole = ref<AdminUser['role']>('agent')
const editActive = ref(true)
const editOrganization = ref<string | null>(null)
const editError = ref('')
const isSelf = computed(() => editing.value?.id === myId.value)
const organizationChoices = computed(() => [
  { value: null, label: '所属なし' },
  ...(organizations.data.value ?? []).map((o) => ({ value: o.id as string | null, label: o.name })),
])

function edit(user: AdminUser) {
  editing.value = user
  editRole.value = user.role
  editActive.value = user.status !== 'inactive'
  editOrganization.value = user.organization?.id ?? null
  editError.value = ''
}

async function saveUser() {
  const user = editing.value
  if (!user) return
  const changes: UserChanges = {}
  if (user.role !== 'requester' && editRole.value !== user.role) changes.role = editRole.value as 'agent' | 'admin'
  if (editActive.value !== (user.status !== 'inactive')) changes.active = editActive.value
  if (user.role === 'requester' && editOrganization.value !== (user.organization?.id ?? null)) {
    changes.organizationId = editOrganization.value
  }
  try {
    if (Object.keys(changes).length > 0) await update.mutateAsync({ id: user.id, changes })
    editing.value = null
  } catch (e) {
    editError.value = adminErrorMessage(e)
  }
}
</script>

<template>
  <AdminFrame title="ユーザー">
    <form
      class="invite"
      aria-label="エージェント・管理者を招待"
      @submit.prevent="sendInvitation"
    >
      <InputText
        v-model="inviteEmail"
        type="email"
        placeholder="招待するメールアドレス"
        aria-label="招待するメールアドレス"
        required
      />
      <Select
        v-model="inviteRole"
        :options="[
          { value: 'agent', label: 'エージェント' },
          { value: 'admin', label: '管理者' },
        ]"
        option-label="label"
        option-value="value"
        aria-label="招待するロール"
      />
      <Button
        type="submit"
        label="招待する"
        :loading="invite.isPending.value"
      />
    </form>
    <Message
      v-if="inviteMessage"
      :severity="inviteMessage.severity"
      class="spaced"
    >
      {{ inviteMessage.text }}
    </Message>

    <div class="filters">
      <Select
        v-model="query.role"
        :options="roleFilter"
        option-label="label"
        option-value="value"
        aria-label="ロールで絞り込む"
      />
      <Select
        v-model="query.status"
        :options="statusFilter"
        option-label="label"
        option-value="value"
        aria-label="状態で絞り込む"
      />
      <form
        role="search"
        @submit.prevent="applySearch"
      >
        <InputText
          v-model="search"
          type="search"
          placeholder="名前・メールアドレス"
          aria-label="ユーザーを検索"
        />
      </form>
    </div>

    <Message
      v-if="isError"
      severity="error"
    >
      ユーザーを読み込めませんでした。
    </Message>
    <DataTable
      v-else
      :value="data?.items ?? []"
      :loading="isPending"
      lazy
      paginator
      :rows="USERS_PAGE_SIZE"
      :first="query.page * USERS_PAGE_SIZE"
      :total-records="data?.total ?? 0"
      data-key="id"
      size="small"
      @page="changePage"
    >
      <template #empty>
        該当するユーザーはいません。
      </template>
      <Column
        field="name"
        header="名前"
      >
        <template #body="{ data: user }">
          {{ user.name || '（未設定）' }}
        </template>
      </Column>
      <Column
        field="email"
        header="メールアドレス"
      />
      <Column header="ロール">
        <template #body="{ data: user }">
          {{ roleLabels[user.role as AdminUser['role']] }}
        </template>
      </Column>
      <Column header="状態">
        <template #body="{ data: user }">
          <span :class="['status', user.status]">{{ userStatusLabels[user.status as AdminUser['status']] }}</span>
        </template>
      </Column>
      <Column header="所属組織">
        <template #body="{ data: user }">
          {{ user.organization?.name ?? '' }}
        </template>
      </Column>
      <Column header="">
        <template #body="{ data: user }">
          <Button
            label="編集"
            size="small"
            text
            :aria-label="`${user.email} を編集`"
            @click="edit(user)"
          />
        </template>
      </Column>
    </DataTable>

    <Dialog
      :visible="editing !== null"
      modal
      :header="editing ? `${editing.name || editing.email} を編集` : ''"
      :style="{ width: '28rem' }"
      @update:visible="(v: boolean) => !v && (editing = null)"
    >
      <form
        v-if="editing"
        @submit.prevent="saveUser"
      >
        <Message
          v-if="editError"
          severity="error"
          class="spaced"
        >
          {{ editError }}
        </Message>
        <p
          v-if="isSelf"
          class="hint"
        >
          自分自身のロールと有効・無効は変更できません。
        </p>
        <FormField
          v-if="editing.role !== 'requester'"
          id="edit-role"
          label="ロール"
        >
          <Select
            v-model="editRole"
            input-id="edit-role"
            :options="[
              { value: 'agent', label: 'エージェント' },
              { value: 'admin', label: '管理者' },
            ]"
            option-label="label"
            option-value="value"
            :disabled="isSelf"
          />
        </FormField>
        <FormField
          v-else
          id="edit-organization"
          label="所属組織"
        >
          <Select
            v-model="editOrganization"
            input-id="edit-organization"
            :options="organizationChoices"
            option-label="label"
            option-value="value"
          />
        </FormField>
        <label class="toggle">
          <ToggleSwitch
            v-model="editActive"
            input-id="edit-active"
            :disabled="isSelf"
          />
          有効（無効にするとログインできなくなります）
        </label>
        <div class="actions">
          <Button
            type="submit"
            label="保存する"
            :loading="update.isPending.value"
          />
          <Button
            label="キャンセル"
            severity="secondary"
            text
            @click="editing = null"
          />
        </div>
      </form>
    </Dialog>
  </AdminFrame>
</template>

<style scoped>
.invite,
.filters {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}
.invite input {
  min-width: 18rem;
}
.spaced {
  margin-bottom: 1rem;
}
.status.inactive,
.status.invited,
.status.unverified {
  color: var(--p-text-muted-color);
}
.hint {
  color: var(--p-text-muted-color);
  margin-top: 0;
}
.toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
</style>
