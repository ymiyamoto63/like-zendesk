<script setup lang="ts">
import AutoComplete, { type AutoCompleteCompleteEvent } from 'primevue/autocomplete'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FormField from '@/features/auth/components/FormField.vue'
import type { Attachment } from '@/shared/attachments'
import AttachmentPicker from '@/shared/components/AttachmentPicker.vue'
import AgentLayout from '@/layouts/AgentLayout.vue'
import TicketFields from '../components/TicketFields.vue'
import type { TicketDraft } from '../draft'
import { ticketErrorMessage } from '../messages'
import { searchUsers, useCreateTicket, useGroups, type UserSummary } from '../queries'

const route = useRoute()
const router = useRouter()
const slug = String(route.params.slug)
const groups = useGroups(slug)
const create = useCreateTicket(slug)

const requester = ref<UserSummary | null>(null)
const suggestions = ref<UserSummary[]>([])
const subject = ref('')
const body = ref('')
const attachments = ref<Attachment[]>([])
const draft = ref<TicketDraft>({
  status: 'new',
  priority: 'normal',
  type: 'question',
  groupId: null,
  assigneeId: null,
  tags: [],
})
const error = ref('')

async function suggest(event: AutoCompleteCompleteEvent) {
  suggestions.value = await searchUsers(slug, event.query)
}

function label(user: UserSummary) {
  return `${user.name} <${user.email}>`
}

async function submit() {
  error.value = ''
  if (!requester.value || typeof requester.value !== 'object') {
    error.value = '依頼者を候補から選んでください。'
    return
  }
  try {
    const ticket = await create.mutateAsync({
      requesterId: requester.value.id,
      subject: subject.value,
      body: body.value,
      priority: draft.value.priority,
      type: draft.value.type,
      groupId: draft.value.groupId ?? undefined,
      assigneeId: draft.value.assigneeId ?? undefined,
      tags: draft.value.tags,
      attachmentIds: attachments.value.map((a) => a.id),
    })
    await router.push(`/t/${slug}/agent/tickets/${ticket.number}`)
  } catch (e) {
    error.value = ticketErrorMessage(e)
  }
}
</script>

<template>
  <AgentLayout>
    <h1>新規チケット</h1>
    <form
      class="new"
      @submit.prevent="submit"
    >
      <section class="main">
        <Message
          v-if="error"
          severity="error"
        >
          {{ error }}
        </Message>
        <FormField
          id="requester"
          label="依頼者"
        >
          <AutoComplete
            v-model="requester"
            input-id="requester"
            :suggestions="suggestions"
            :option-label="label"
            placeholder="名前かメールアドレスで検索"
            :delay="200"
            fluid
            @complete="suggest"
          />
        </FormField>
        <FormField
          id="subject"
          label="件名"
        >
          <InputText
            id="subject"
            v-model="subject"
            maxlength="250"
            required
          />
        </FormField>
        <FormField
          id="body"
          label="本文（依頼者に公開されます）"
        >
          <Textarea
            id="body"
            v-model="body"
            rows="8"
            auto-resize
            required
          />
        </FormField>
        <AttachmentPicker
          v-model="attachments"
          :slug="slug"
          class="spaced"
        />
        <Button
          type="submit"
          label="作成する"
          :loading="create.isPending.value"
        />
      </section>
      <aside>
        <TicketFields
          v-model="draft"
          :groups="groups.data.value ?? []"
        />
      </aside>
    </form>
  </AgentLayout>
</template>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 1.25rem;
}
.new {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 18rem;
  gap: 1.5rem;
  align-items: start;
}
@media (max-width: 900px) {
  .new {
    grid-template-columns: 1fr;
  }
}
.spaced {
  margin-bottom: 1rem;
}
</style>
