/** 一覧の並べ替え。URL と API では `-updatedAt` のように書き、先頭の `-` が降順。 */
export type SortState = { field: string; order: 1 | -1 } | null

export function parseSort(sort: string | undefined): SortState {
  const first = sort?.split(',')[0]
  if (!first) return null
  return first.startsWith('-') ? { field: first.slice(1), order: -1 } : { field: first, order: 1 }
}

export function formatSort(state: SortState): string {
  if (!state) return ''
  return state.order === -1 ? `-${state.field}` : state.field
}
