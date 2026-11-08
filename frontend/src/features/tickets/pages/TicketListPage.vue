<script setup lang="ts">
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable, { type DataTablePageEvent, type DataTableSortEvent } from 'primevue/datatable'
import Message from 'primevue/message'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AgentLayout from '@/layouts/AgentLayout.vue'
import ViewSidebar from '@/features/views/components/ViewSidebar.vue'
import { DEFAULT_VIEW, useViews } from '@/features/views/queries'
import { useSessionStore } from '@/session/store'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import { formatDateTime, priorityLabels } from '@/shared/labels'
import BulkUpdatePanel from '../components/BulkUpdatePanel.vue'
import { PAGE_SIZE, useGroups, useTicketList, type ListQuery, type Ticket } from '../queries'
import { formatSort, parseSort } from '../sort'

// ビューの一覧（/agent/views/:viewId）と全文検索（/agent/search?q=）を兼ねる。
const route = useRoute()
const router = useRouter()
const slug = String(route.params.slug)
const views = useViews(slug)

const searching = computed(() => route.params.viewId === undefined)
const query = computed<ListQuery>(() => ({
  view: searching.value ? 'builtin:all' : String(route.params.viewId ?? DEFAULT_VIEW),
  q: searching.value ? String(route.query.q ?? '').trim() : '',
  sort: typeof route.query.sort === 'string' ? route.query.sort : '',
  // URL のページ番号は 1 から。API は 0 から
  page: Math.max(Number(route.query.page ?? 1) - 1, 0),
}))
const sort = computed(() => parseSort(query.value.sort))
const { data, isPending, isError } = useTicketList(slug, query)
const groups = useGroups(slug)

// 一括更新で選んだ行。ビューやページが変わったら選択を外す
const selected = ref<Ticket[]>([])
watch(query, () => (selected.value = []))
const bulkMessage = ref<{ severity: 'success' | 'warn'; text: string } | null>(null)
watch(selected, (rows) => {
  if (rows.length > 0) bulkMessage.value = null
})
function finished(result: { severity: 'success' | 'warn'; text: string }) {
  selected.value = []
  bulkMessage.value = result
}

const view = computed(() => views.data.value?.find((v) => v.id === query.value.view))
const title = computed(() => (searching.value ? `「${query.value.q}」の検索結果` : (view.value?.name ?? 'ビュー')))
const editable = computed(() => {
  const me = useSessionStore().me[slug]
  return view.value?.kind === 'personal' || (view.value?.kind === 'shared' && me?.role === 'admin')
})

function changePage(event: DataTablePageEvent) {
  void router.push({ query: { ...route.query, page: event.page + 1 } })
}

function changeSort(event: DataTableSortEvent) {
  const field = typeof event.sortField === 'string' ? event.sortField : null
  const next = field ? formatSort({ field, order: event.sortOrder === -1 ? -1 : 1 }) : undefined
  void router.push({ query: { ...route.query, sort: next, page: undefined } })
}
</script>

<template>
  <AgentLayout>
    <div class="list">
      <ViewSidebar
        :slug="slug"
        :active="searching ? undefined : query.view"
      />
      <section class="tickets">
        <header>
          <h1>
            {{ title }}
            <span
              v-if="data"
              class="total"
            >{{ data.total }} 件</span>
          </h1>
          <RouterLink
            v-if="editable"
            :to="`/t/${slug}/agent/views/${encodeURIComponent(query.view)}/edit`"
          >
            <Button
              label="ビューを編集"
              severity="secondary"
              size="small"
              text
            />
          </RouterLink>
        </header>
        <Message
          v-if="isError"
          severity="error"
        >
          チケットを読み込めませんでした。
        </Message>
        <Message
          v-if="bulkMessage"
          :severity="bulkMessage.severity"
          closable
          class="bulk-message"
          @close="bulkMessage = null"
        >
          {{ bulkMessage.text }}
        </Message>
        <BulkUpdatePanel
          v-if="selected.length > 0"
          :slug="slug"
          :numbers="selected.map((t) => t.number)"
          :groups="groups.data.value ?? []"
          @done="finished"
          @cancel="selected = []"
        />
        <DataTable
          v-if="!isError"
          v-model:selection="selected"
          :value="data?.items ?? []"
          :loading="isPending"
          lazy
          paginator
          :rows="PAGE_SIZE"
          :first="query.page * PAGE_SIZE"
          :total-records="data?.total ?? 0"
          :sort-field="sort?.field"
          :sort-order="sort?.order"
          removable-sort
          data-key="number"
          size="small"
          @page="changePage"
          @sort="changeSort"
        >
          <template #empty>
            {{ searching ? '一致するチケットはありません。' : 'このビューにチケットはありません。' }}
          </template>
          <Column
            selection-mode="multiple"
            class="select"
          />
          <Column
            field="number"
            header="#"
            class="number"
            sortable
          />
          <Column
            field="subject"
            header="件名"
            sortable
          >
            <template #body="{ data: ticket }">
              <RouterLink :to="`/t/${slug}/agent/tickets/${ticket.number}`">
                {{ ticket.subject }}
              </RouterLink>
            </template>
          </Column>
          <Column
            field="status"
            header="状態"
            sortable
          >
            <template #body="{ data: ticket }">
              <StatusBadge :status="ticket.status" />
            </template>
          </Column>
          <Column
            field="priority"
            header="優先度"
            sortable
          >
            <template #body="{ data: ticket }">
              {{ priorityLabels[ticket.priority as keyof typeof priorityLabels] }}
            </template>
          </Column>
          <Column
            field="requester.name"
            header="依頼者"
          />
          <Column header="担当者">
            <template #body="{ data: ticket }">
              {{ ticket.assignee?.name ?? '—' }}
            </template>
          </Column>
          <Column
            field="updatedAt"
            header="更新"
            sortable
          >
            <template #body="{ data: ticket }">
              {{ formatDateTime(ticket.updatedAt) }}
            </template>
          </Column>
        </DataTable>
      </section>
    </div>
  </AgentLayout>
</template>

<style scoped>
.list {
  display: flex;
  gap: 1.5rem;
  align-items: flex-start;
}
.tickets {
  flex: 1;
  min-width: 0;
}
header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
h1 {
  margin-top: 0;
  font-size: 1.25rem;
}
.total {
  margin-left: 0.5rem;
  font-size: 0.875rem;
  font-weight: normal;
  color: var(--p-text-muted-color);
}
.bulk-message {
  margin-bottom: 0.75rem;
}
:deep(.select) {
  width: 2.5rem;
}
:deep(.number) {
  width: 4rem;
  color: var(--p-text-muted-color);
}
</style>
