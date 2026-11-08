import type { components } from '@/api/schema'
import { priorityLabels, statusLabels, typeLabels } from '@/shared/labels'

export type Condition = components['schemas']['ConditionDto']
export type Field = Condition['field']
export type Op = Condition['op']
export type SortField = components['schemas']['SortDto']['field']

/** 閲覧者によって決まる値（API と同じ）。 */
export const ME = '@me'
export const MY_GROUPS = '@my_groups'

export const fieldLabels: Record<Field, string> = {
  status: '状態',
  priority: '優先度',
  type: '種別',
  assignee: '担当者',
  group: 'グループ',
  tags: 'タグ',
  created_at: '作成日',
  updated_at: '更新日',
}

export const opLabels: Record<Op, string> = {
  in: 'がいずれか',
  not_in: 'がいずれでもない',
  eq: 'が',
  is_null: 'が未設定',
  is_not_null: 'が設定済み',
  has_any: 'をいずれか含む',
  has_all: 'をすべて含む',
  within_days: 'が N 日以内',
  older_than_days: 'が N 日より前',
}

/** 項目ごとに使える演算子。API の許可リスト（TicketFilter）と同じ。 */
export const opsByField: Record<Field, Op[]> = {
  status: ['in', 'not_in'],
  priority: ['in', 'not_in'],
  type: ['in', 'not_in'],
  assignee: ['eq', 'is_null', 'is_not_null'],
  group: ['in', 'is_null', 'is_not_null'],
  tags: ['has_any', 'has_all'],
  created_at: ['within_days', 'older_than_days'],
  updated_at: ['within_days', 'older_than_days'],
}

export const sortLabels: Record<SortField, string> = {
  number: '番号',
  subject: '件名',
  status: '状態',
  priority: '優先度',
  type: '種別',
  created_at: '作成日時',
  updated_at: '更新日時',
}

/** 値の入力欄の種類。 */
export type ValueKind = 'none' | 'choices' | 'user' | 'tags' | 'days'

export function valueKind(op: Op): ValueKind {
  switch (op) {
    case 'is_null':
    case 'is_not_null':
      return 'none'
    case 'eq':
      return 'user'
    case 'has_any':
    case 'has_all':
      return 'tags'
    case 'within_days':
    case 'older_than_days':
      return 'days'
    default:
      return 'choices'
  }
}

/** 列挙型の項目の選択肢。グループは画面が読み込んだ一覧から作る。 */
export function enumChoices(field: Field): { value: string; label: string }[] {
  const labels: Record<string, string> | undefined = {
    status: statusLabels,
    priority: priorityLabels,
    type: typeLabels,
  }[field as 'status' | 'priority' | 'type']
  return labels ? Object.entries(labels).map(([value, label]) => ({ value, label })) : []
}

function defaultValue(kind: ValueKind): unknown {
  switch (kind) {
    case 'none':
      return null
    case 'user':
      return ME
    case 'days':
      return 7
    default:
      return []
  }
}

/** 新しい条件の行。 */
export function newCondition(field: Field = 'status'): Condition {
  return withOp({ field, op: opsByField[field][0], value: null }, opsByField[field][0])
}

/** 項目を変えたら、演算子と値を初期値に戻す。 */
export function withField(condition: Condition, field: Field): Condition {
  return condition.field === field ? condition : newCondition(field)
}

/** 演算子を変えたとき、値の形が同じなら値を残す。 */
export function withOp(condition: Condition, op: Op): Condition {
  const kind = valueKind(op)
  const keep = valueKind(condition.op) === kind && condition.value !== null
  return { ...condition, op, value: keep ? condition.value : defaultValue(kind) }
}

/** 保存前の検査。問題があれば利用者に見せる文を返す。 */
export function problem(conditions: Condition[]): string | null {
  for (const c of conditions) {
    const kind = valueKind(c.op)
    const label = fieldLabels[c.field]
    if ((kind === 'choices' || kind === 'tags') && (!Array.isArray(c.value) || c.value.length === 0)) {
      return `「${label}」の値を選んでください。`
    }
    if (kind === 'user' && !c.value) return `「${label}」を選んでください。`
    if (kind === 'days') {
      const days = c.value
      if (typeof days !== 'number' || !Number.isInteger(days) || days < 1 || days > 3650) {
        return `「${label}」の日数は 1〜3650 で入力してください。`
      }
    }
  }
  return null
}
