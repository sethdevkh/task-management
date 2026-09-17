import { TASK_STATUSES, type TaskStatus } from '@/types/domain'

const LABELS: Record<TaskStatus, string> = {
  TO_DO: 'To do',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
}

export function statusLabel(status: TaskStatus): string {
  return LABELS[status]
}

export const STATUS_OPTIONS = [
  TASK_STATUSES.TO_DO,
  TASK_STATUSES.IN_PROGRESS,
  TASK_STATUSES.COMPLETED,
] as const
