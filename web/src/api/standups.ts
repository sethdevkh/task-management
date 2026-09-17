import { ApiError, apiFetch } from '@/api/client'
import { ROLES, type Role } from '@/types/domain'

export type ApiStandup = {
  id: number
  userId: number
  displayName: string
  standupDate: string
  done: string
  doing: string
  blockers: string
  createdAt: string
  updatedAt: string
  editable: boolean
}

export type ApiStandupMember = {
  id: number
  displayName: string
  role: Role
}

export type LeadStandupBoard = {
  date: string
  submitted: ApiStandup[]
  missing: ApiStandupMember[]
}

function isRole(value: unknown): value is Role {
  return value === ROLES.TEAM_LEAD || value === ROLES.TEAM_MEMBER
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parseStandup(value: unknown): ApiStandup {
  if (!isRecord(value)) {
    throw new ApiError(500, 'Invalid standup')
  }
  const {
    id,
    userId,
    displayName,
    standupDate,
    done,
    doing,
    blockers,
    createdAt,
    updatedAt,
    editable,
  } = value
  const doneText = done == null ? '' : done
  const doingText = doing == null ? '' : doing
  const blockersText = blockers == null ? '' : blockers
  if (
    typeof id !== 'number' ||
    typeof userId !== 'number' ||
    typeof displayName !== 'string' ||
    typeof standupDate !== 'string' ||
    typeof doneText !== 'string' ||
    typeof doingText !== 'string' ||
    typeof blockersText !== 'string' ||
    typeof createdAt !== 'string' ||
    typeof updatedAt !== 'string' ||
    typeof editable !== 'boolean'
  ) {
    throw new ApiError(500, 'Invalid standup')
  }
  return {
    id,
    userId,
    displayName,
    standupDate,
    done: doneText,
    doing: doingText,
    blockers: blockersText,
    createdAt,
    updatedAt,
    editable,
  }
}

function parseMember(value: unknown): ApiStandupMember {
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

export async function listMyStandups(): Promise<ApiStandup[]> {
  const response = await apiFetch('/api/standups')
  const body: unknown = await readJson(response, 'Could not load standups')
  if (!Array.isArray(body)) {
    throw new ApiError(response.status, 'Could not load standups')
  }
  return body.map(parseStandup)
}

export async function upsertTodayStandup(input: {
  done: string
  doing: string
  blockers: string
}): Promise<ApiStandup> {
  const response = await apiFetch('/api/standups', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      done: input.done,
      doing: input.doing,
      blockers: input.blockers,
    }),
  })
  return parseStandup(await readJson(response, 'Could not save standup'))
}

export async function listLeadStandups(date?: string): Promise<LeadStandupBoard> {
  const params = new URLSearchParams()
  if (date) {
    params.set('date', date)
  }
  const query = params.toString()
  const response = await apiFetch(`/api/lead/standups${query ? `?${query}` : ''}`)
  const body: unknown = await readJson(response, 'Could not load team standups')
  if (
    !isRecord(body) ||
    typeof body.date !== 'string' ||
    !Array.isArray(body.submitted) ||
    !Array.isArray(body.missing)
  ) {
    throw new ApiError(response.status, 'Could not load team standups')
  }
  return {
    date: body.date,
    submitted: body.submitted.map(parseStandup),
    missing: body.missing.map(parseMember),
  }
}
