import { Navigate } from 'react-router-dom'
import { useSession } from '@/auth/session'
import { ROLES } from '@/types/domain'

export function HomeRedirect() {
  const { session } = useSession()
  if (!session) {
    return <Navigate to="/login" replace />
  }
  return (
    <Navigate to={session.role === ROLES.TEAM_LEAD ? '/dashboard' : '/tasks'} replace />
  )
}
