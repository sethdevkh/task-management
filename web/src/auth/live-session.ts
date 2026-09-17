import { MOCK_USERS } from '@/mock/seed'
import type { Role } from '@/types/domain'

type JwtPayload = {
  sub?: string
  uid?: number
  role?: string
  displayName?: string
  exp?: number
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const part = token.split('.')[1]
    if (!part) {
      return null
    }
    const padded = part.replace(/-/g, '+').replace(/_/g, '/')
    const json = atob(padded)
    return JSON.parse(json) as JwtPayload
  } catch {
    return null
  }
}

export function isJwtExpired(token: string): boolean {
  const payload = decodeJwtPayload(token)
  if (!payload || typeof payload.exp !== 'number') {
    return true
  }
  return payload.exp * 1000 <= Date.now()
}

const EMAIL_TO_MOCK_ID: Record<string, string> = {
  'casey@demo.local': 'user-casey',
  'alex@demo.local': 'user-alex',
  'bailey@demo.local': 'user-bailey',
}

export function mockUserIdForLiveSession(email: string | undefined, displayName: string, role: Role): string {
  if (email && EMAIL_TO_MOCK_ID[email]) {
    return EMAIL_TO_MOCK_ID[email]
  }
  return MOCK_USERS.find((user) => user.displayName === displayName && user.role === role)?.id
    ?? `live:${displayName}`
}
