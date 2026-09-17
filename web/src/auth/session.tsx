import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setAuthToken, setOnUnauthorized } from '@/api/client'
import { AUTH_MODE } from '@/config/auth-mode'
import { decodeJwtPayload, isJwtExpired, mockUserIdForLiveSession } from '@/auth/live-session'
import type { LoginResponse } from '@/api/auth'
import type { Session, User } from '@/types/domain'

const STORAGE_KEY = 'task-management.session'

function readSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as Session
    if (!parsed.userId || !parsed.role || !parsed.displayName) {
      return null
    }
    if (AUTH_MODE === 'live') {
      if (!parsed.token || isJwtExpired(parsed.token)) {
        sessionStorage.removeItem(STORAGE_KEY)
        return null
      }
    }
    return parsed
  } catch {
    return null
  }
}

function persist(session: Session | null): void {
  if (!session) {
    sessionStorage.removeItem(STORAGE_KEY)
    setAuthToken(null)
    return
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  setAuthToken(session.token)
}

type SessionValue = {
  session: Session | null
  signInMock: (user: User) => void
  signInLive: (response: LoginResponse) => void
  signOut: () => void
}

const SessionContext = createContext<SessionValue | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => {
    const restored = readSession()
    setAuthToken(restored?.token ?? null)
    return restored
  })

  const signInMock = useCallback((user: User) => {
    const next: Session = {
      userId: user.id,
      displayName: user.displayName,
      role: user.role,
      token: null,
    }
    persist(next)
    setSession(next)
  }, [])

  const signInLive = useCallback((response: LoginResponse) => {
    const payload = decodeJwtPayload(response.token)
    const next: Session = {
      userId: mockUserIdForLiveSession(payload?.sub, response.displayName, response.role),
      displayName: response.displayName,
      role: response.role,
      token: response.token,
    }
    persist(next)
    setSession(next)
  }, [])

  const signOut = useCallback(() => {
    persist(null)
    setSession(null)
  }, [])

  useEffect(() => {
    setOnUnauthorized(() => {
      persist(null)
      setSession(null)
    })
    return () => setOnUnauthorized(null)
  }, [])

  const value = useMemo(
    () => ({ session, signInMock, signInLive, signOut }),
    [session, signInMock, signInLive, signOut],
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
