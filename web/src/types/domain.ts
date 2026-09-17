export const ROLES = {
  TEAM_MEMBER: 'TEAM_MEMBER',
  TEAM_LEAD: 'TEAM_LEAD',
} as const

export type Role = (typeof ROLES)[keyof typeof ROLES]

export const TASK_STATUSES = {
  TO_DO: 'TO_DO',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
} as const

export type TaskStatus = (typeof TASK_STATUSES)[keyof typeof TASK_STATUSES]

export type User = {
  id: string
  displayName: string
  role: Role
}

export type Task = {
  id: string
  title: string
  description: string
  status: TaskStatus
  assigneeId: string
  creatorId: string
  dueDate: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export type Standup = {
  id: string
  userId: string
  standupDate: string
  done: string
  doing: string
  blockers: string
}

export type MockSession = {
  userId: string
  displayName: string
  role: Role
}
