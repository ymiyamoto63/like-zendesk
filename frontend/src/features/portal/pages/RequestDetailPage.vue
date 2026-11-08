<script setup lang="ts">
import Button from 'primevue/button'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { errorMessage } from '@/features/auth/messages'
import { ticketErrorMessage } from '@/features/tickets/messages'
import PortalLayout from '@/layouts/PortalLayout.vue'
import { formatDateTime } from '@/shared/labels'
import PortalStatus from '../components/PortalStatus.vue'
import { canSolve, replyNotice } from '../labels'
import { useRequest, useRequestMutations } from '../queries'

const route = useRoute()
const router = useRouter()
const slug = String(route.params.slug)
const number = Number(route.params.number)
const { ticket, comments } = useRequest(slug, number)
const { reply, solve } = useRequestMutations(slug, number)

const body = ref('')
const error = ref('')
// 起票直後とクローズ後の返信からの起票では、移動先のこの画面で案内を出す
const notice = ref(
  route.query.submitted
    ? 'お問い合わせを受け付けました。確認のメールをお送りしました。'
    : route.query.followUp
      ? replyNotice('closed', 'new', true)
      : '',
)
if (notice.value) void router.replace({ query: {} })
const notFound = computed(() => ticket.error.value instanceof ApiError && ticket.error.value.status === 404)
const solvable = computed(() => (ticket.data.value ? canSolve(ticket.data.value.status, ticket.data.value.assigneeName) : false))

async function send() {
  const current = ticket.data.value
  if (!current || !body.value.trim()) return
  error.value = ''
  try {
    const result = await reply.mutateAsync(body.value)
    body.value = ''
    if (result.followUp) {
      await router.push({ path: `/t/${slug}/requests/${result.ticket.number}`, query: { followUp: '1' } })
      return
    }
    notice.value = replyNotice(current.status, result.ticket.status, false)
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function markSolved() {
  error.value = ''
  try {
    await solve.mutateAsync()
    notice.value = '解決済みにしました。また困ったときは、このページから返信できます。'
  } catch (e) {
    error.value = ticketErrorMessage(e)
  }
}
</script>

<template>
  <PortalLayout>
    <p class="back">
      <RouterLink :to="`/t/${slug}/requests`">
        ← お問い合わせ一覧
      </RouterLink>
    </p>
    <Message
      v-if="notFound"
      severity="warn"
    >
      このお問い合わせは見つかりません。
    </Message>
    <template v-else-if="ticket.data.value">
      <header class="head">
        <h1>{{ ticket.data.value.subject }}</h1>
        <p class="meta">
          <PortalStatus :status="ticket.data.value.status" />
          <span>#{{ ticket.data.value.number }}</span>
          <span>担当: {{ ticket.data.value.assigneeName ?? '未定' }}</span>
          <RouterLink
            v-if="ticket.data.value.followUpOf"
            :to="`/t/${slug}/requests/${ticket.data.value.followUpOf}`"
          >
            #{{ ticket.data.value.followUpOf }} の続き
          </RouterLink>
        </p>
      </header>
      <Message
        v-if="notice"
        severity="success"
        closable
        @close="notice = ''"
      >
        {{ notice }}
      </Message>
      <Message
        v-if="error"
        severity="error"
      >
        {{ error }}
      </Message>
      <ol class="conversation">
        <li
          v-for="comment in comments.data.value?.items ?? []"
          :key="comment.id"
          :class="{ mine: comment.mine }"
        >
          <header>
            <strong>{{ comment.mine ? 'あなた' : comment.authorName }}</strong>
            <time>{{ formatDateTime(comment.createdAt) }}</time>
          </header>
          <p class="body">
            {{ comment.body }}
          </p>
        </li>
      </ol>
      <form
        class="reply"
        @submit.prevent="send"
      >
        <label for="reply-body">返信</label>
        <Textarea
          id="reply-body"
          v-model="body"
          rows="4"
          auto-resize
          :placeholder="ticket.data.value.status === 'closed' ? '返信すると、新しいお問い合わせとして受け付けます。' : '担当者への返信'"
        />
        <div class="actions">
          <Button
            type="submit"
            label="返信を送信"
            :disabled="!body.trim()"
            :loading="reply.isPending.value"
          />
          <Button
            v-if="solvable"
            label="解決したので閉じる"
            severity="secondary"
            outlined
            :loading="solve.isPending.value"
            @click="markSolved"
          />
        </div>
      </form>
    </template>
  </PortalLayout>
</template>

<style scoped>
.back {
  margin: 0 0 0.75rem;
  font-size: 0.875rem;
}
h1 {
  margin: 0;
  font-size: 1.25rem;
  overflow-wrap: anywhere;
}
.meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}
.conversation {
  list-style: none;
  margin: 1rem 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.conversation li {
  max-width: 90%;
  padding: 0.75rem 1rem;
  border: 1px solid var(--p-content-border-color);
  border-radius: 8px;
  background: var(--p-content-background);
}
.conversation li.mine {
  align-self: flex-end;
  background: var(--p-highlight-background);
}
.conversation header {
  display: flex;
  gap: 0.75rem;
  font-size: 0.875rem;
}
.conversation time {
  color: var(--p-text-muted-color);
}
.body {
  margin: 0.5rem 0 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.reply {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.reply label {
  font-weight: 600;
}
.actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}
</style>
