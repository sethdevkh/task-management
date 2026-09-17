import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { InvalidCredentialsError, login } from '@/api/auth'
import { useSession } from '@/auth/session'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { AUTH_MODE } from '@/config/auth-mode'
import { MOCK_USERS } from '@/mock/seed'
import { ROLES } from '@/types/domain'

function homePath(role: string): string {
  return role === ROLES.TEAM_LEAD ? '/dashboard' : '/tasks'
}

export function LoginPage() {
  if (AUTH_MODE === 'mock') {
    return <MockLoginForm />
  }
  return <LiveLoginForm />
}

function LiveLoginForm() {
  const { signInLive } = useSession()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setPending(true)
    try {
      const response = await login(email.trim(), password)
      signInLive(response)
      navigate(homePath(response.role), { replace: true })
    } catch (cause) {
      if (cause instanceof InvalidCredentialsError) {
        setError('Invalid credentials')
      } else {
        setError('Sign-in failed. Try again.')
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <Card>
        <CardHeader>
          <CardTitle>Sign in</CardTitle>
          <CardDescription>
            Seeded email and password. Role comes from the server, not this form.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4" onSubmit={onSubmit}>
            {error ? (
              <Alert variant="destructive">
                <AlertTitle>Could not sign in</AlertTitle>
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            <div className="grid gap-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={pending}>
              {pending ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

function MockLoginForm() {
  const { signInMock } = useSession()
  const navigate = useNavigate()
  const [userId, setUserId] = useState(MOCK_USERS[1]?.id ?? MOCK_USERS[0].id)

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const user = MOCK_USERS.find((candidate) => candidate.id === userId)
    if (!user) {
      return
    }
    signInMock(user)
    navigate(homePath(user.role), { replace: true })
  }

  return (
    <div className="mx-auto max-w-md">
      <Alert className="mb-4">
        <AlertTitle>Mock login</AlertTitle>
        <AlertDescription>
          Auth mode is <code>{AUTH_MODE}</code>. This path is local-only (
          <code>VITE_AUTH_MODE=mock</code>). It is not built into the production image.
        </AlertDescription>
      </Alert>
      <Card>
        <CardHeader>
          <CardTitle>Sign in</CardTitle>
          <CardDescription>Client-only role for UI wiring without the API.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4" onSubmit={onSubmit}>
            <div className="grid gap-2">
              <Label htmlFor="mock-user">User</Label>
              <Select value={userId} onValueChange={setUserId}>
                <SelectTrigger id="mock-user" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {MOCK_USERS.map((user) => (
                    <SelectItem key={user.id} value={user.id}>
                      {user.displayName} ({user.role === ROLES.TEAM_LEAD ? 'lead' : 'member'})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Button type="submit">Continue</Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
