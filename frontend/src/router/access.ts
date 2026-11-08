import type { Role } from '@/api/client'

/** 画面に必要なログイン状態。最終的な権限の判定は API が行い、これは表示の切り替えのためだけにある。 */
export type Requires = 'public' | 'requester' | 'staff' | 'admin'

export type Access = 'allow' | 'login' | 'agent' | 'not_found'

/**
 * - 未ログインならログイン画面へ（`login`）
 * - スタッフがポータルを開いたらエージェント画面へ（`agent`）
 * - それ以外でロールが合わなければ 404（`not_found`）
 */
export function access(requires: Requires, role: Role | null): Access {
  if (requires === 'public') return 'allow'
  if (role === null) return 'login'
  switch (requires) {
    case 'requester':
      return role === 'requester' ? 'allow' : 'agent'
    case 'staff':
      return role === 'agent' || role === 'admin' ? 'allow' : 'not_found'
    case 'admin':
      return role === 'admin' ? 'allow' : 'not_found'
  }
}
