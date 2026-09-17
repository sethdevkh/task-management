import { useMemo, useState, type FormEvent } from 'react'
import { useSession } from '@/auth/session'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { utcDateString } from '@/lib/dates'
import { useMockStore } from '@/mock/store'

export function StandupPage() {
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
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const history = useMemo(() => {
    if (!userId) {
      return []
    }
    return standups
      .filter((entry) => entry.userId === userId)
      .slice()
      .sort((a, b) => b.standupDate.localeCompare(a.standupDate))
  }, [userId, standups])

  if (!session || !userId) {
    return null
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!userId) {
      return
    }
    if (!done.trim() && !doing.trim() && !blockers.trim()) {
      setError('Fill at least one of Done, Doing, or Blockers.')
      setSaved(false)
      return
    }
    upsertStandup({
      userId,
      done: done.trim(),
      doing: doing.trim(),
      blockers: blockers.trim(),
    })
    setError(null)
    setSaved(true)
  }

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="font-heading text-2xl font-medium">Today standup</h1>
        <p className="text-sm text-muted-foreground">
          One entry per UTC day ({today}). Submit again today to update the same row.
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>UTC {today}</CardTitle>
          <CardDescription>
            {mineToday ? 'Editing today’s saved entry.' : 'No entry yet for today.'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-3" onSubmit={onSubmit}>
            <div className="grid gap-2">
              <Label htmlFor="standup-done">Done</Label>
              <Textarea
                id="standup-done"
                value={done}
                onChange={(event) => setDone(event.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="standup-doing">Doing</Label>
              <Textarea
                id="standup-doing"
                value={doing}
                onChange={(event) => setDoing(event.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="standup-blockers">Blockers</Label>
              <Textarea
                id="standup-blockers"
                value={blockers}
                onChange={(event) => setBlockers(event.target.value)}
              />
            </div>
            {error ? (
              <Alert variant="destructive">
                <AlertTitle>Nothing to save</AlertTitle>
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            {saved && !error ? (
              <p className="text-sm text-muted-foreground">Saved for {today} UTC.</p>
            ) : null}
            <Button type="submit" className="w-fit">
              Save today
            </Button>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>History</CardTitle>
          <CardDescription>Past days are read-only in this mock.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          {history.length === 0 ? (
            <p className="text-sm text-muted-foreground">No standups yet.</p>
          ) : (
            history.map((entry) => (
              <div key={entry.id} className="rounded-lg border p-3 text-sm">
                <p className="font-medium">
                  {entry.standupDate}
                  {entry.standupDate === today ? ' (today)' : ''}
                </p>
                <p>Done: {entry.done || '—'}</p>
                <p>Doing: {entry.doing || '—'}</p>
                <p>Blockers: {entry.blockers || '—'}</p>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  )
}
