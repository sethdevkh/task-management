import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import type { MockSession, User } from '@/types/domain'

const STORAGE_KEY = 'task-management.mock-session'

function readSession(): MockSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as MockSession
    if (!parsed.userId || !parsed.role || !parsed.displayName) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

type SessionValue = {
  session: MockSession | null
  signIn: (user: User) => void
  signOut: () => void
}

const SessionContext = createContext<SessionValue | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<MockSession | null>(() => readSession())

  const signIn = useCallback((user: User) => {
    const next: MockSession = {
      userId: user.id,
      displayName: user.displayName,
      role: user.role,
    }
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setSession(next)
  }, [])

  const signOut = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY)
    setSession(null)
  }, [])

  const value = useMemo(
    () => ({ session, signIn, signOut }),
    [session, signIn, signOut],
  )

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext)
  if (!value) {
    throw new Error('useSession must be used inside SessionProvider')
  }
  return value
}
