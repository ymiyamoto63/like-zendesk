import { api, unwrap } from '@/api/client'
import type { components } from '@/api/schema'

export type Attachment = components['schemas']['AttachmentResponse']

/** 1 ファイルの上限（API と同じ、F-T-7）。 */
export const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
export const MAX_ATTACHMENTS = 10

/** 添付をアップロードし、コメントや起票に付ける ID を受け取る（2 段階のアップロードの 1 段目）。 */
export function uploadAttachment(slug: string, file: File): Promise<Attachment> {
  const form = new FormData()
  form.append('file', file)
  return unwrap(
    api.POST('/api/t/{slug}/attachments', {
      params: { path: { slug } },
      // openapi-fetch に JSON にさせず、ブラウザに multipart の境界を付けさせる
      body: form as unknown as { file: string },
      bodySerializer: (body) => body as unknown as FormData,
    }),
  )
}

export function attachmentUrl(slug: string, id: string): string {
  return `/api/t/${encodeURIComponent(slug)}/attachments/${encodeURIComponent(id)}`
}

/** 1.2 MB のように表示する。 */
export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10 * 1024 ? 1 : 0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
