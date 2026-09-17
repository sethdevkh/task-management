import type { User } from '@/types/domain'

export function userName(users: User[], userId: string): string {
  return users.find((user) => user.id === userId)?.displayName ?? userId
}
