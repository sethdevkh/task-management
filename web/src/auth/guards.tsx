import { Navigate, useLocation } from 'react-router-dom'
import { useSession } from '@/auth/session'
import { ROLES } from '@/types/domain'
import type { ReactNode } from 'react'

export function RequireSession({ children }: { children: ReactNode }) {
  const { session } = useSession()
  const location = useLocation()
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return children
}

export function RequireLead({ children }: { children: ReactNode }) {
  const { session } = useSession()
  const location = useLocation()
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (session.role !== ROLES.TEAM_LEAD) {
    return <Navigate to="/tasks" replace state={{ denied: 'lead-only' }} />
  }
  return children
}

export function GuestOnly({ children }: { children: ReactNode }) {
  const { session } = useSession()
  if (session) {
    return <Navigate to="/" replace />
  }
  return children
}
