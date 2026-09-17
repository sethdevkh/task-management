import { Badge } from '@/components/ui/badge'
import { statusLabel } from '@/lib/status'
import type { TaskStatus } from '@/types/domain'

const VARIANTS: Record<TaskStatus, 'outline' | 'secondary' | 'default'> = {
  TO_DO: 'outline',
  IN_PROGRESS: 'secondary',
  COMPLETED: 'default',
}

export function StatusBadge({ status }: { status: TaskStatus }) {
  return <Badge variant={VARIANTS[status]}>{statusLabel(status)}</Badge>
}
