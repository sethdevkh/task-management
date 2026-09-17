import { useEffect, useMemo, useState } from 'react'
import { ApiError } from '@/api/client'
import { listLeadStandups, type ApiStandup, type ApiStandupMember } from '@/api/standups'
import { useSession } from '@/auth/session'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AUTH_MODE } from '@/config/auth-mode'
import { utcDateString } from '@/lib/dates'
import { userName } from '@/lib/users'
import { useMockStore } from '@/mock/store'

type Submitted = {
  id: string
  displayName: string
  done: string
  doing: string
  blockers: string
}

type Missing = {
  id: string
  displayName: string
}

function fromApiSubmitted(entry: ApiStandup): Submitted {
  return {
    id: String(entry.id),
    displayName: entry.displayName,
    done: entry.done,
    doing: entry.doing,
    blockers: entry.blockers,
  }
}

function fromApiMissing(member: ApiStandupMember): Missing {
  return { id: String(member.id), displayName: member.displayName }
}

export function TeamStandupsPage() {
  return AUTH_MODE === 'live' ? <LiveTeamStandupsPage /> : <MockTeamStandupsPage />
}

function LiveTeamStandupsPage() {
  const { session } = useSession()
  const [date, setDate] = useState(utcDateString())
  const [submitted, setSubmitted] = useState<Submitted[]>([])
  const [missing, setMissing] = useState<Missing[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listLeadStandups(date)
      .then((board) => {
        if (cancelled) {
          return
        }
        setSubmitted(board.submitted.map(fromApiSubmitted))
        setMissing(board.missing.map(fromApiMissing))
        setError(null)
      })
      .catch((err: unknown) => {
        if (!cancelled && !(err instanceof ApiError && err.status === 401)) {
          setError(err instanceof Error ? err.message : 'Could not load team standups')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [date])

  if (!session) {
    return null
  }

  return (
    <TeamStandupsView
      live
      date={date}
      submitted={submitted}
      missing={missing}
      loading={loading}
      error={error}
      onDate={(next) => {
        setLoading(true)
        setDate(next)
      }}
    />
  )
}

function MockTeamStandupsPage() {
  const { session } = useSession()
  const { users, standups } = useMockStore()
  const [date, setDate] = useState(utcDateString())

  const submitted = useMemo(
    () =>
      standups
        .filter((entry) => entry.standupDate === date)
        .map((entry) => ({
          id: entry.id,
          displayName: userName(users, entry.userId),
          done: entry.done,
          doing: entry.doing,
          blockers: entry.blockers,
        })),
    [date, standups, users],
  )
  const missing = useMemo(() => {
    const submittedIds = new Set(
      standups.filter((entry) => entry.standupDate === date).map((entry) => entry.userId),
    )
    return users
      .filter((user) => !submittedIds.has(user.id))
      .map((user) => ({ id: user.id, displayName: user.displayName }))
  }, [date, standups, users])

  if (!session) {
    return null
  }

  return (
    <TeamStandupsView
      live={false}
      date={date}
      submitted={submitted}
      missing={missing}
      onDate={(next) => setDate(next)}
    />
  )
}

function TeamStandupsView({
  live,
  date,
  submitted,
  missing,
  loading = false,
  error = null,
  onDate,
}: {
  live: boolean
  date: string
  submitted: Submitted[]
  missing: Missing[]
  loading?: boolean
  error?: string | null
  onDate: (value: string) => Promise<void> | void
}) {
  return (
    <div className="grid gap-6">
      <div>
        <h1 className="font-heading text-2xl font-medium">Team standups</h1>
        <p className="text-sm text-muted-foreground">
          {live
            ? 'Lead-only API. Members who call this endpoint get 403; hiding the route is not authorization.'
            : 'Lead-only mock screen. Members cannot open this route in the UI. That is not API authorization.'}
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Date</CardTitle>
          <CardDescription>UTC calendar date. Default is today.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-2">
          <Label htmlFor="standup-board-date">Standup date</Label>
          <Input
            id="standup-board-date"
            type="date"
            className="w-48"
            value={date}
            onChange={(event) => {
              void onDate(event.target.value)
            }}
          />
        </CardContent>
      </Card>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {loading ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Submitted</CardTitle>
            <CardDescription>{submitted.length} of the team for {date} UTC.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3">
            {submitted.length === 0 ? (
              <p className="text-sm text-muted-foreground">No standups for this date.</p>
            ) : (
              submitted.map((entry) => (
                <div key={entry.id} className="rounded-lg border p-3 text-sm">
                  <p className="font-medium">{entry.displayName}</p>
                  <p>Done: {entry.done || '—'}</p>
                  <p>Doing: {entry.doing || '—'}</p>
                  <p>Blockers: {entry.blockers || '—'}</p>
                </div>
              ))
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Missing</CardTitle>
            <CardDescription>Team users with no row for this UTC date.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {missing.length === 0 ? (
              <p className="text-muted-foreground">Everyone submitted.</p>
            ) : (
              missing.map((member) => (
                <div key={member.id} className="flex justify-between gap-4">
                  <span>{member.displayName}</span>
                  <span className="text-destructive">Missing</span>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
