import { ApiError, apiFetch } from '@/api/client'
import { ROLES, type Role } from '@/types/domain'

export class InvalidCredentialsError extends Error {
  constructor() {
    super('Invalid credentials')
    this.name = 'InvalidCredentialsError'
  }
}

export type LoginResponse = {
  token: string
  role: Role
  displayName: string
}

function isRole(value: unknown): value is Role {
  return value === ROLES.TEAM_LEAD || value === ROLES.TEAM_MEMBER
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const response = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (response.status === 401) {
    throw new InvalidCredentialsError()
  }
  if (!response.ok) {
    throw new ApiError(response.status, 'Login failed')
  }
  const body: unknown = await response.json()
  if (
    typeof body !== 'object' ||
    body === null ||
    !('token' in body) ||
    !('role' in body) ||
    !('displayName' in body)
  ) {
    throw new ApiError(response.status, 'Login failed')
  }
  const token = body.token
  const role = body.role
  const displayName = body.displayName
  if (typeof token !== 'string' || typeof displayName !== 'string' || !isRole(role)) {
    throw new ApiError(response.status, 'Login failed')
  }
  return { token, role, displayName }
}
