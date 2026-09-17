const raw = import.meta.env.VITE_AUTH_MODE

export type AuthMode = 'mock' | 'live'

export const AUTH_MODE: AuthMode = raw === 'mock' ? 'mock' : 'live'
