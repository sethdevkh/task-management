import { ApiError, apiFetch } from '@/api/client'
import { ROLES, TASK_STATUSES, type Role, type TaskStatus } from '@/types/domain'

export type DashboardMember = {
  id: number
  displayName: string
  role: Role
}

export type MemberWorkload = DashboardMember & {
  toDo: number
  inProgress: number
}

export type StatusMix = Record<TaskStatus, number>

export type CompletionRate = {
  rate: number
  completedInLast7Days: number
  totalTasks: number
}

export type LeadDashboard = {
  workload: MemberWorkload[]
  statusMix: StatusMix
  completion: CompletionRate
  standupPresence: {
    date: string
    submitted: DashboardMember[]
    missing: DashboardMember[]
  }
}

function isRole(value: unknown): value is Role {
  return value === ROLES.TEAM_LEAD || value === ROLES.TEAM_MEMBER
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parseMember(value: unknown): DashboardMember {
  if (!isRecord(value)) {
    throw new ApiError(500, 'Invalid dashboard member')
  }
  const { id, displayName, role } = value
  if (typeof id !== 'number' || typeof displayName !== 'string' || !isRole(role)) {
    throw new ApiError(500, 'Invalid dashboard member')
  }
  return { id, displayName, role }
}

function parseWorkload(value: unknown): MemberWorkload {
  if (!isRecord(value)) {
    throw new ApiError(500, 'Invalid dashboard workload')
  }
  const member = parseMember(value)
  const { toDo, inProgress } = value
  if (typeof toDo !== 'number' || typeof inProgress !== 'number') {
    throw new ApiError(500, 'Invalid dashboard workload')
  }
  return { ...member, toDo, inProgress }
}

function parseStatusMix(value: unknown): StatusMix {
  if (!isRecord(value)) {
    throw new ApiError(500, 'Invalid status mix')
  }
  const toDo = value[TASK_STATUSES.TO_DO]
  const inProgress = value[TASK_STATUSES.IN_PROGRESS]
  const completed = value[TASK_STATUSES.COMPLETED]
  if (typeof toDo !== 'number' || typeof inProgress !== 'number' || typeof completed !== 'number') {
    throw new ApiError(500, 'Invalid status mix')
  }
  return {
    TO_DO: toDo,
    IN_PROGRESS: inProgress,
    COMPLETED: completed,
  }
}

function parseCompletion(value: unknown): CompletionRate {
  if (!isRecord(value)) {
    throw new ApiError(500, 'Invalid completion rate')
  }
  const { rate, completedInLast7Days, totalTasks } = value
  if (
    typeof rate !== 'number' ||
    typeof completedInLast7Days !== 'number' ||
    typeof totalTasks !== 'number'
  ) {
    throw new ApiError(500, 'Invalid completion rate')
  }
  return { rate, completedInLast7Days, totalTasks }
}

async function throwFor(response: Response, fallback: string): Promise<never> {
  let message = fallback
  try {
    const body: unknown = await response.json()
    if (isRecord(body) && typeof body.error === 'string') {
      message = body.error
    }
  } catch {
    // keep fallback
  }
  throw new ApiError(response.status, message)
}

async function readJson(response: Response, fallback: string): Promise<unknown> {
  if (response.status === 401) {
    throw new ApiError(401, 'Unauthorized')
  }
  if (!response.ok) {
    await throwFor(response, fallback)
  }
  return response.json()
}

export function emptyDashboard(date: string): LeadDashboard {
  return {
    workload: [],
    statusMix: {
      TO_DO: 0,
      IN_PROGRESS: 0,
      COMPLETED: 0,
    },
    completion: {
      rate: 0,
      completedInLast7Days: 0,
      totalTasks: 0,
    },
    standupPresence: {
      date,
      submitted: [],
      missing: [],
    },
  }
}

export async function getLeadDashboard(): Promise<LeadDashboard> {
  const response = await apiFetch('/api/lead/dashboard')
  const body: unknown = await readJson(response, 'Could not load dashboard')
  if (
    !isRecord(body) ||
    !Array.isArray(body.workload) ||
    !isRecord(body.standupPresence) ||
    typeof body.standupPresence.date !== 'string' ||
    !Array.isArray(body.standupPresence.submitted) ||
    !Array.isArray(body.standupPresence.missing)
  ) {
    throw new ApiError(response.status, 'Could not load dashboard')
  }
  return {
    workload: body.workload.map(parseWorkload),
    statusMix: parseStatusMix(body.statusMix),
    completion: parseCompletion(body.completion),
    standupPresence: {
      date: body.standupPresence.date,
      submitted: body.standupPresence.submitted.map(parseMember),
      missing: body.standupPresence.missing.map(parseMember),
    },
  }
}
