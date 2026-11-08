<script setup lang="ts">
import Button from 'primevue/button'
import Message from 'primevue/message'
import Paginator, { type PageState } from 'primevue/paginator'
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import PortalLayout from '@/layouts/PortalLayout.vue'
import { formatDateTime } from '@/shared/labels'
import PortalStatus from '../components/PortalStatus.vue'
import { PORTAL_PAGE_SIZE, useMyRequests } from '../queries'

// ポータルのトップ（/t/:slug）と自分のチケット一覧（/t/:slug/requests）を兼ねる
const route = useRoute()
const router = useRouter()
const slug = String(route.params.slug)
const page = computed(() => Math.max(Number(route.query.page ?? 1) - 1, 0))
const { data, isPending, isError } = useMyRequests(slug, page)

function changePage(event: PageState) {
  void router.push({ query: { ...route.query, page: event.page + 1 } })
}
</script>

<template>
  <PortalLayout>
    <div class="head">
      <h1>お問い合わせ</h1>
      <RouterLink :to="`/t/${slug}/requests/new`">
        <Button label="新しいお問い合わせ" />
      </RouterLink>
    </div>
    <Message
      v-if="isError"
      severity="error"
    >
      お問い合わせを読み込めませんでした。
    </Message>
    <p
      v-else-if="!isPending && data?.total === 0"
      class="empty"
    >
      まだお問い合わせはありません。困っていることがあれば「新しいお問い合わせ」から送ってください。
    </p>
    <ul
      v-else
      class="requests"
    >
      <li
        v-for="ticket in data?.items ?? []"
        :key="ticket.number"
      >
        <RouterLink :to="`/t/${slug}/requests/${ticket.number}`">
          <span class="subject">{{ ticket.subject }}</span>
          <span class="meta">
            <PortalStatus :status="ticket.status" />
            <span>#{{ ticket.number }} ・ 更新 {{ formatDateTime(ticket.updatedAt) }}</span>
          </span>
        </RouterLink>
      </li>
    </ul>
    <Paginator
      v-if="(data?.total ?? 0) > PORTAL_PAGE_SIZE"
      :rows="PORTAL_PAGE_SIZE"
      :first="page * PORTAL_PAGE_SIZE"
      :total-records="data?.total ?? 0"
      @page="changePage"
    />
  </PortalLayout>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}
h1 {
  margin: 0;
  font-size: 1.25rem;
}
.empty {
  color: var(--p-text-muted-color);
}
.requests {
  list-style: none;
  margin: 1rem 0;
  padding: 0;
  border: 1px solid var(--p-content-border-color);
  border-radius: 8px;
  background: var(--p-content-background);
}
.requests li + li {
  border-top: 1px solid var(--p-content-border-color);
}
.requests a {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.75rem 1rem;
  color: var(--p-text-color);
  text-decoration: none;
}
.requests a:hover {
  background: var(--p-content-hover-background);
}
.subject {
  font-weight: 600;
}
.meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  font-size: 0.875rem;
  color: var(--p-text-muted-color);
}
</style>
