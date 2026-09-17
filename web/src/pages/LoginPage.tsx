import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSession } from '@/auth/session'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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

export function LoginPage() {
  const { signIn } = useSession()
  const navigate = useNavigate()
  const [userId, setUserId] = useState(MOCK_USERS[1]?.id ?? MOCK_USERS[0].id)

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const user = MOCK_USERS.find((candidate) => candidate.id === userId)
    if (!user) {
      return
    }
    signIn(user)
    navigate(user.role === ROLES.TEAM_LEAD ? '/dashboard' : '/tasks', { replace: true })
  }

  return (
    <div className="mx-auto max-w-md">
      <Alert className="mb-4">
        <AlertTitle>Mock login</AlertTitle>
        <AlertDescription>
          Auth mode is <code>{AUTH_MODE}</code>. Pick a seeded user. No password is stored, no JWT
          is minted, and nothing is sent to an API.
        </AlertDescription>
      </Alert>
      <Card>
        <CardHeader>
          <CardTitle>Sign in</CardTitle>
          <CardDescription>Client-only role for UI wiring. This is not authorization.</CardDescription>
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
