<script setup lang="ts">
import Button from 'primevue/button'
import Message from 'primevue/message'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import AgentLayout from '@/layouts/AgentLayout.vue'
import StatusBadge from '@/shared/components/StatusBadge.vue'
import { formatDateTime } from '@/shared/labels'
import CommentComposer from '../components/CommentComposer.vue'
import TicketFields from '../components/TicketFields.vue'
import { changesOf, draftOf, type TicketDraft } from '../draft'
import { ticketErrorMessage } from '../messages'
import { useGroups, useTicket, useTicketMutations } from '../queries'
import { buildTimeline } from '../timeline'

const route = useRoute()
const slug = String(route.params.slug)
const number = Number(route.params.number)

const { ticket, comments, events } = useTicket(slug, number)
const groups = useGroups(slug)
const { update, comment, reload } = useTicketMutations(slug, number)

const draft = ref<TicketDraft | null>(null)
const body = ref('')
const isPublic = ref(true)
const error = ref('')
const notice = ref('')

// 読み込み・更新のたびに、編集中の項目を最新の内容に合わせる（入力中のコメントは残す）
watch(
  () => ticket.data.value,
  (t) => {
    if (t) draft.value = draftOf(t)
  },
  { immediate: true },
)

const closed = computed(() => ticket.data.value?.status === 'closed')
const changes = computed(() =>
  ticket.data.value && draft.value ? changesOf(ticket.data.value, draft.value) : null,
)
const timeline = computed(() => buildTimeline(comments.data.value ?? [], events.data.value?.items ?? []))
const status = computed({
  get: () => draft.value?.status ?? 'open',
  set: (value) => {
    if (draft.value) draft.value = { ...draft.value, status: value }
  },
})

async function handle(action: () => Promise<unknown>) {
  error.value = ''
  notice.value = ''
  try {
    await action()
    return true
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) {
      await reload()
      notice.value = '他の人がこのチケットを更新しました。最新の内容を読み込んだので、確認してからもう一度送信してください。'
    } else {
      error.value = ticketErrorMessage(e)
    }
    return false
  }
}

async function saveFields() {
  const body = changes.value
  if (body) await handle(() => update.mutateAsync(body))
}

async function submitComment() {
  const ok = await handle(() =>
    comment.mutateAsync({ body: body.value, public: isPublic.value, ticket: changes.value ?? undefined }),
  )
  if (ok) {
    body.value = ''
    void reload()
  }
}
</script>

<template>
  <AgentLayout>
    <Message
      v-if="ticket.isError.value"
      severity="error"
    >
      チケットが見つかりません。
    </Message>
    <div
      v-else-if="ticket.data.value && draft"
      class="detail"
    >
      <section class="main">
        <header>
          <h1>
            <span class="number">#{{ ticket.data.value.number }}</span>
            {{ ticket.data.value.subject }}
          </h1>
          <StatusBadge :status="ticket.data.value.status" />
        </header>
        <p class="meta">
          依頼者 {{ ticket.data.value.requester.name }} ・ 作成 {{ formatDateTime(ticket.data.value.createdAt) }}
          <template v-if="ticket.data.value.followUpOf">
            ・
            <RouterLink :to="`/t/${slug}/agent/tickets/${ticket.data.value.followUpOf}`">
              #{{ ticket.data.value.followUpOf }} の続き
            </RouterLink>
          </template>
        </p>
        <Message
          v-if="notice"
          severity="warn"
        >
          {{ notice }}
        </Message>
        <Message
          v-if="error"
          severity="error"
        >
          {{ error }}
        </Message>

        <ol class="timeline">
          <li
            v-for="(item, i) in timeline"
            :key="i"
          >
            <article
              v-if="item.kind === 'comment'"
              class="comment"
              :class="{ internal: !item.comment.public }"
            >
              <header>
                <strong>{{ item.comment.author.name }}</strong>
                <span class="label">{{ item.comment.public ? '公開' : '社内メモ' }}</span>
                <time>{{ formatDateTime(item.comment.createdAt) }}</time>
              </header>
              <p class="body">
                {{ item.comment.body }}
              </p>
            </article>
            <p
              v-else-if="item.kind === 'created'"
              class="event"
            >
              {{ item.actor }} がチケットを作成 ・ {{ formatDateTime(item.at) }}
            </p>
            <p
              v-else
              class="event"
            >
              {{ item.lines.join(' ／ ') }}（{{ item.actor }}） ・ {{ formatDateTime(item.at) }}
            </p>
          </li>
        </ol>

        <Message
          v-if="closed"
          severity="secondary"
        >
          クローズしたチケットは変更できません。
        </Message>
        <CommentComposer
          v-else
          v-model:body="body"
          v-model:is-public="isPublic"
          v-model:status="status"
          :submitting="comment.isPending.value"
          @submit="submitComment"
        />
      </section>

      <aside class="side">
        <TicketFields
          v-model="draft"
          :groups="groups.data.value ?? []"
          show-status
          :disabled="closed"
        />
        <Button
          label="項目を更新"
          severity="secondary"
          :disabled="!changes || closed"
          :loading="update.isPending.value"
          fluid
          @click="saveFields"
        />
      </aside>
    </div>
  </AgentLayout>
</template>

<style scoped>
.detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 18rem;
  gap: 1.5rem;
  align-items: start;
}
.main header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
h1 {
  margin: 0;
  font-size: 1.25rem;
}
.number,
.meta,
.event,
time {
  color: var(--p-text-muted-color);
}
.meta {
  margin-top: 0.25rem;
}
.timeline {
  display: grid;
  gap: 0.75rem;
  padding: 0;
  list-style: none;
}
.comment {
  padding: 0.75rem 1rem;
  border: 1px solid var(--p-content-border-color);
  border-radius: var(--p-content-border-radius);
  background: var(--p-content-background);
}
.comment.internal {
  background: var(--p-amber-50);
  border-color: var(--p-amber-300);
}
.comment header {
  gap: 0.5rem;
}
.comment .label {
  font-size: 0.75rem;
  padding: 0 0.4rem;
  border-radius: 0.25rem;
  background: var(--p-surface-100);
}
.comment.internal .label {
  background: var(--p-amber-200);
}
.body {
  margin: 0.5rem 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.event {
  margin: 0;
  font-size: 0.875rem;
}
.side {
  display: grid;
  gap: 1rem;
  position: sticky;
  top: 1rem;
}
@media (max-width: 900px) {
  .detail {
    grid-template-columns: 1fr;
  }
}
</style>
