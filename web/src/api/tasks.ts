import { ApiError, apiFetch } from '@/api/client'
import { ROLES, TASK_STATUSES, type Role, type TaskStatus } from '@/types/domain'

export type ApiTask = {
  id: number
  title: string
  description: string
  status: TaskStatus
  assigneeId: number
  assigneeDisplayName: string
  creatorId: number
  creatorDisplayName: string
  dueDate: string | null
  createdAt: string
  updatedAt: string
  completedAt: string | null
  canDelete: boolean
}

export type ApiTeamMember = {
  id: number
  displayName: string
  role: Role
}

export type LeadTaskList = {
  tasks: ApiTask[]
  members: ApiTeamMember[]
}

function isTaskStatus(value: unknown): value is TaskStatus {
  return (
    value === TASK_STATUSES.TO_DO ||
    value === TASK_STATUSES.IN_PROGRESS ||
    value === TASK_STATUSES.COMPLETED
  )
}

function isRole(value: unknown): value is Role {
  return value === ROLES.TEAM_LEAD || value === ROLES.TEAM_MEMBER
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parseTask(value: unknown): ApiTask {
  if (!isRecord(value)) {
    throw new ApiError(500, 'Invalid task')
  }
  const {
    id,
    title,
    description,
    status,
    assigneeId,
    assigneeDisplayName,
    creatorId,
    creatorDisplayName,
    dueDate,
    createdAt,
    updatedAt,
    completedAt,
    canDelete,
  } = value
  const descriptionText = description == null ? '' : description
  if (
    typeof id !== 'number' ||
    typeof title !== 'string' ||
    typeof descriptionText !== 'string' ||
    !isTaskStatus(status) ||
    typeof assigneeId !== 'number' ||
    typeof assigneeDisplayName !== 'string' ||
    typeof creatorId !== 'number' ||
    typeof creatorDisplayName !== 'string' ||
    typeof createdAt !== 'string' ||
    typeof updatedAt !== 'string' ||
    typeof canDelete !== 'boolean' ||
    !(dueDate === null || dueDate === undefined || typeof dueDate === 'string') ||
    !(completedAt === null || completedAt === undefined || typeof completedAt === 'string')
  ) {
    throw new ApiError(500, 'Invalid task')
  }
  return {
    id,
    title,
    description: descriptionText,
    status,
    assigneeId,
    assigneeDisplayName,
    creatorId,
    creatorDisplayName,
    dueDate: typeof dueDate === 'string' ? dueDate : null,
    createdAt,
    updatedAt,
    completedAt: typeof completedAt === 'string' ? completedAt : null,
    canDelete,
  }
}

function parseMember(value: unknown): ApiTeamMember {
  if (!isRecord(value)) {
    throw new ApiError(500, 'Invalid member')
  }
  const { id, displayName, role } = value
  if (typeof id !== 'number' || typeof displayName !== 'string' || !isRole(role)) {
    throw new ApiError(500, 'Invalid member')
  }
  return { id, displayName, role }
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

export async function listMyTasks(): Promise<ApiTask[]> {
  const response = await apiFetch('/api/tasks')
  const body: unknown = await readJson(response, 'Could not load tasks')
  if (!Array.isArray(body)) {
    throw new ApiError(response.status, 'Could not load tasks')
  }
  return body.map(parseTask)
}

export async function createTask(input: {
  title: string
  description?: string
  dueDate?: string | null
  assigneeId?: number
  status?: TaskStatus
}): Promise<ApiTask> {
  const response = await apiFetch('/api/tasks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      title: input.title,
      description: input.description ?? '',
      dueDate: input.dueDate || null,
      ...(input.assigneeId !== undefined ? { assigneeId: input.assigneeId } : {}),
      ...(input.status ? { status: input.status } : {}),
    }),
  })
  return parseTask(await readJson(response, 'Could not create task'))
}

export async function updateTask(
  id: number,
  patch: { title?: string; description?: string; status?: TaskStatus; dueDate?: string | null },
): Promise<ApiTask> {
  const response = await apiFetch(`/api/tasks/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  })
  return parseTask(await readJson(response, 'Could not update task'))
}

export async function deleteTask(id: number): Promise<void> {
  const response = await apiFetch(`/api/tasks/${id}`, { method: 'DELETE' })
  if (response.status === 401) {
    throw new ApiError(401, 'Unauthorized')
  }
  if (response.status === 204) {
    return
  }
  await throwFor(response, 'Could not delete task')
}

export async function listLeadTasks(filters?: {
  assigneeId?: number
  status?: TaskStatus
}): Promise<LeadTaskList> {
  const params = new URLSearchParams()
  if (filters?.assigneeId !== undefined) {
    params.set('assigneeId', String(filters.assigneeId))
  }
  if (filters?.status) {
    params.set('status', filters.status)
  }
  const query = params.toString()
  const response = await apiFetch(`/api/lead/tasks${query ? `?${query}` : ''}`)
  const body: unknown = await readJson(response, 'Could not load team tasks')
  if (!isRecord(body) || !Array.isArray(body.tasks) || !Array.isArray(body.members)) {
    throw new ApiError(response.status, 'Could not load team tasks')
  }
  return {
    tasks: body.tasks.map(parseTask),
    members: body.members.map(parseMember),
  }
}

export async function assignTask(id: number, assigneeId: number): Promise<ApiTask> {
  const response = await apiFetch(`/api/lead/tasks/${id}/assignee`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ assigneeId }),
  })
  return parseTask(await readJson(response, 'Could not assign task'))
}
