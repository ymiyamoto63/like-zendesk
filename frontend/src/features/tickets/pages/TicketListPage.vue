<script setup lang="ts">
import Column from 'primevue/column'
import DataTable, { type DataTablePageEvent } from 'primevue/datatable'
import Message from 'primevue/message'
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AgentLayout from '@/layouts/AgentLayout.vue'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import { formatDateTime, priorityLabels } from '@/shared/labels'
import { PAGE_SIZE, useTicketList } from '../queries'

const route = useRoute()
const router = useRouter()
const slug = String(route.params.slug)
// URL のページ番号は 1 から。API は 0 から
const page = computed(() => Math.max(Number(route.query.page ?? 1) - 1, 0))
const { data, isPending, isError } = useTicketList(slug, page)

function changePage(event: DataTablePageEvent) {
  void router.push({ query: { ...route.query, page: event.page + 1 } })
}
</script>

<template>
  <AgentLayout>
    <h1>チケット</h1>
    <Message
      v-if="isError"
      severity="error"
    >
      チケットを読み込めませんでした。
    </Message>
    <DataTable
      v-else
      :value="data?.items ?? []"
      :loading="isPending"
      lazy
      paginator
      :rows="PAGE_SIZE"
      :first="page * PAGE_SIZE"
      :total-records="data?.total ?? 0"
      data-key="number"
      size="small"
      @page="changePage"
    >
      <template #empty>
        チケットはまだありません。
      </template>
      <Column
        field="number"
        header="#"
        class="number"
      />
      <Column header="件名">
        <template #body="{ data: ticket }">
          <RouterLink :to="`/t/${slug}/agent/tickets/${ticket.number}`">
            {{ ticket.subject }}
          </RouterLink>
        </template>
      </Column>
      <Column header="状態">
        <template #body="{ data: ticket }">
          <StatusBadge :status="ticket.status" />
        </template>
      </Column>
      <Column header="優先度">
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
      <Column header="更新">
        <template #body="{ data: ticket }">
          {{ formatDateTime(ticket.updatedAt) }}
        </template>
      </Column>
    </DataTable>
  </AgentLayout>
</template>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 1.25rem;
}
:deep(.number) {
  width: 4rem;
  color: var(--p-text-muted-color);
}
</style>
