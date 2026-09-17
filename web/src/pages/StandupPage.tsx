import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError } from '@/api/client'
import { listMyStandups, upsertTodayStandup, type ApiStandup } from '@/api/standups'
import { useSession } from '@/auth/session'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { AUTH_MODE } from '@/config/auth-mode'
import { utcDateString } from '@/lib/dates'
import { useMockStore } from '@/mock/store'
import type { Standup } from '@/types/domain'

type HistoryEntry = {
  id: string
  standupDate: string
  done: string
  doing: string
  blockers: string
  today: boolean
}

function toHistory(entry: { id: string | number; standupDate: string; done: string; doing: string; blockers: string }, today: string): HistoryEntry {
  return {
    id: String(entry.id),
    standupDate: entry.standupDate,
    done: entry.done,
    doing: entry.doing,
    blockers: entry.blockers,
    today: entry.standupDate === today,
  }
}

export function StandupPage() {
  return AUTH_MODE === 'live' ? <LiveStandupPage /> : <MockStandupPage />
}

function LiveStandupPage() {
  const { session } = useSession()
  const today = utcDateString()
  const [history, setHistory] = useState<HistoryEntry[]>([])
  const [done, setDone] = useState('')
  const [doing, setDoing] = useState('')
  const [blockers, setBlockers] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [saving, setSaving] = useState(false)
  const mineToday = history.find((entry) => entry.today)

  useEffect(() => {
    let cancelled = false
    listMyStandups()
      .then((entries) => {
        if (cancelled) {
          return
        }
        const next = entries.map((entry: ApiStandup) => toHistory(entry, today))
        setHistory(next)
        const todayEntry = next.find((entry) => entry.today)
        setDone(todayEntry?.done ?? '')
        setDoing(todayEntry?.doing ?? '')
        setBlockers(todayEntry?.blockers ?? '')
        setError(null)
      })
      .catch((err: unknown) => {
        if (!cancelled && !(err instanceof ApiError && err.status === 401)) {
          setError(err instanceof Error ? err.message : 'Could not load standups')
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
  }, [today])

  if (!session) {
    return null
  }

  return (
    <StandupView
      live
      today={today}
      history={history}
      loading={loading}
      error={error}
      done={done}
      doing={doing}
      blockers={blockers}
      formError={formError}
      saved={saved}
      saving={saving}
      hasToday={Boolean(mineToday)}
      onDone={setDone}
      onDoing={setDoing}
      onBlockers={setBlockers}
      onSubmit={async () => {
        if (!done.trim() && !doing.trim() && !blockers.trim()) {
          setFormError('Fill at least one of Done, Doing, or Blockers.')
          setSaved(false)
          return
        }
        setSaving(true)
        try {
          const savedEntry = await upsertTodayStandup({
            done: done.trim(),
            doing: doing.trim(),
            blockers: blockers.trim(),
          })
          const next = toHistory(savedEntry, today)
          setHistory((current) => {
            const withoutToday = current.filter((entry) => !entry.today)
            return [next, ...withoutToday]
          })
          setDone(next.done)
          setDoing(next.doing)
          setBlockers(next.blockers)
          setFormError(null)
          setSaved(true)
        } catch (err: unknown) {
          if (!(err instanceof ApiError && err.status === 401)) {
            setFormError(err instanceof Error ? err.message : 'Could not save standup')
          }
          setSaved(false)
        } finally {
          setSaving(false)
        }
      }}
    />
  )
}

function MockStandupPage() {
  const { session } = useSession()
  const { standups, upsertStandup } = useMockStore()
  const today = utcDateString()
  const userId = session?.userId
  const mineToday = standups.find(
    (entry) => entry.userId === userId && entry.standupDate === today,
  )
  const [done, setDone] = useState(mineToday?.done ?? '')
  const [doing, setDoing] = useState(mineToday?.doing ?? '')
  const [blockers, setBlockers] = useState(mineToday?.blockers ?? '')
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const history = useMemo(() => {
    if (!userId) {
      return []
    }
    return standups
      .filter((entry: Standup) => entry.userId === userId)
      .slice()
      .sort((a, b) => b.standupDate.localeCompare(a.standupDate))
      .map((entry) => toHistory(entry, today))
  }, [userId, standups, today])

  if (!session || !userId) {
    return null
  }

  return (
    <StandupView
      live={false}
      today={today}
      history={history}
      done={done}
      doing={doing}
      blockers={blockers}
      formError={formError}
      saved={saved}
      hasToday={Boolean(mineToday)}
      onDone={setDone}
      onDoing={setDoing}
      onBlockers={setBlockers}
      onSubmit={() => {
        if (!done.trim() && !doing.trim() && !blockers.trim()) {
          setFormError('Fill at least one of Done, Doing, or Blockers.')
          setSaved(false)
          return
        }
        upsertStandup({
          userId,
          done: done.trim(),
          doing: doing.trim(),
          blockers: blockers.trim(),
        })
        setFormError(null)
        setSaved(true)
      }}
    />
  )
}

function StandupView({
  live,
  today,
  history,
  loading = false,
  error = null,
  done,
  doing,
  blockers,
  formError,
  saved,
  saving = false,
  hasToday,
  onDone,
  onDoing,
  onBlockers,
  onSubmit,
}: {
  live: boolean
  today: string
  history: HistoryEntry[]
  loading?: boolean
  error?: string | null
  done: string
  doing: string
  blockers: string
  formError: string | null
  saved: boolean
  saving?: boolean
  hasToday: boolean
  onDone: (value: string) => void
  onDoing: (value: string) => void
  onBlockers: (value: string) => void
  onSubmit: () => Promise<void> | void
}) {
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await onSubmit()
  }

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="font-heading text-2xl font-medium">Today standup</h1>
        <p className="text-sm text-muted-foreground">
          One entry per UTC day ({today}). Submit again today to update the same row.
          {live ? ' Saved on the API. Do not send a team id.' : ' Mock data stays in memory.'}
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>UTC {today}</CardTitle>
          <CardDescription>
            {hasToday ? 'Editing today’s saved entry.' : 'No entry yet for today.'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-3" onSubmit={(event) => void submit(event)}>
            <div className="grid gap-2">
              <Label htmlFor="standup-done">Done</Label>
              <Textarea
                id="standup-done"
                value={done}
                onChange={(event) => onDone(event.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="standup-doing">Doing</Label>
              <Textarea
                id="standup-doing"
                value={doing}
                onChange={(event) => onDoing(event.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="standup-blockers">Blockers</Label>
              <Textarea
                id="standup-blockers"
                value={blockers}
                onChange={(event) => onBlockers(event.target.value)}
              />
            </div>
            {formError ? (
              <Alert variant="destructive">
                <AlertTitle>Nothing to save</AlertTitle>
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            ) : null}
            {saved && !formError ? (
              <p className="text-sm text-muted-foreground">Saved for {today} UTC.</p>
            ) : null}
            <Button type="submit" className="w-fit" disabled={saving}>
              Save today
            </Button>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>History</CardTitle>
          <CardDescription>
            {live
              ? 'Past days are read-only. Editing them on the API returns 403.'
              : 'Past days are read-only in this mock.'}
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          {loading ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
          {!loading && history.length === 0 && !error ? (
            <p className="text-sm text-muted-foreground">No standups yet.</p>
          ) : null}
          {history.map((entry) => (
            <div key={entry.id} className="rounded-lg border p-3 text-sm">
              <p className="font-medium">
                {entry.standupDate}
                {entry.today ? ' (today)' : ''}
              </p>
              <p>Done: {entry.done || '—'}</p>
              <p>Doing: {entry.doing || '—'}</p>
              <p>Blockers: {entry.blockers || '—'}</p>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
