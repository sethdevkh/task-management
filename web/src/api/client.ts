import { API_BASE_URL } from '@/api/config'

type UnauthorizedHandler = () => void

let authToken: string | null = null
let onUnauthorized: UnauthorizedHandler | null = null

export function setAuthToken(token: string | null): void {
  authToken = token
}

export function setOnUnauthorized(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`)
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: 'omit',
  })
  if (response.status === 401 && path !== '/api/auth/login') {
    onUnauthorized?.()
  }
  return response
}
