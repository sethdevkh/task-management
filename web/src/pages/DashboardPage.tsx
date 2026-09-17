import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '@/api/client'
import { emptyDashboard, getLeadDashboard, type LeadDashboard } from '@/api/dashboard'
import { useSession } from '@/auth/session'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { AUTH_MODE } from '@/config/auth-mode'
import { utcDateString } from '@/lib/dates'
import { useMockStore } from '@/mock/store'
import { TASK_STATUSES } from '@/types/domain'

const MIX_ORDER = [TASK_STATUSES.TO_DO, TASK_STATUSES.IN_PROGRESS, TASK_STATUSES.COMPLETED] as const

type PresenceRow = {
  key: string
  displayName: string
  submitted: boolean
}

type WorkloadRow = {
  key: string
  displayName: string
  toDo: number
  inProgress: number
}

export function DashboardPage() {
  return AUTH_MODE === 'live' ? <LiveDashboardPage /> : <MockDashboardPage />
}

function LiveDashboardPage() {
  const { session } = useSession()
  const [data, setData] = useState<LeadDashboard>(() => emptyDashboard(utcDateString()))
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const next = await getLeadDashboard()
    setData(next)
  }, [])

  useEffect(() => {
    let cancelled = false
    getLeadDashboard()
      .then((next) => {
        if (cancelled) {
          return
        }
        setData(next)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!cancelled && !(err instanceof ApiError && err.status === 401)) {
          setError(err instanceof Error ? err.message : 'Could not load dashboard')
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
  }, [])

  if (!session) {
    return null
  }

  return (
    <DashboardView
      live
      workload={data.workload.map((user) => ({
        key: String(user.id),
        displayName: user.displayName,
        toDo: user.toDo,
        inProgress: user.inProgress,
      }))}
      statusMix={data.statusMix}
      completion={data.completion}
      presenceDate={data.standupPresence.date}
      presence={[
        ...data.standupPresence.submitted.map((user) => ({
          key: String(user.id),
          displayName: user.displayName,
          submitted: true,
        })),
        ...data.standupPresence.missing.map((user) => ({
          key: String(user.id),
          displayName: user.displayName,
          submitted: false,
        })),
      ]}
      loading={loading}
      error={error}
      onRefresh={() => {
        setLoading(true)
        setError(null)
        void reload()
          .then(() => setError(null))
          .catch((err: unknown) => {
            if (!(err instanceof ApiError && err.status === 401)) {
              setError(err instanceof Error ? err.message : 'Could not load dashboard')
            }
          })
          .finally(() => setLoading(false))
      }}
    />
  )
}

function MockDashboardPage() {
  const { users, tasks, standups } = useMockStore()
  const [today] = useState(() => utcDateString())
  const [weekAgo] = useState(() => Date.now() - 7 * 24 * 60 * 60 * 1000)

  const mix = {
    TO_DO: tasks.filter((task) => task.status === TASK_STATUSES.TO_DO).length,
    IN_PROGRESS: tasks.filter((task) => task.status === TASK_STATUSES.IN_PROGRESS).length,
    COMPLETED: tasks.filter((task) => task.status === TASK_STATUSES.COMPLETED).length,
  }

  const completedRecently = tasks.filter(
    (task) =>
      task.status === TASK_STATUSES.COMPLETED && task.completedAt && Date.parse(task.completedAt) >= weekAgo,
  ).length

  return (
    <DashboardView
      live={false}
      workload={users.map((user) => {
        const assigned = tasks.filter((task) => task.assigneeId === user.id)
        return {
          key: user.id,
          displayName: user.displayName,
          toDo: assigned.filter((task) => task.status === TASK_STATUSES.TO_DO).length,
          inProgress: assigned.filter((task) => task.status === TASK_STATUSES.IN_PROGRESS).length,
        }
      })}
      statusMix={mix}
      completion={{
        rate: tasks.length === 0 ? 0 : completedRecently / tasks.length,
        completedInLast7Days: completedRecently,
        totalTasks: tasks.length,
      }}
      presenceDate={today}
      presence={users.map((user) => ({
        key: user.id,
        displayName: user.displayName,
        submitted: standups.some((entry) => entry.userId === user.id && entry.standupDate === today),
      }))}
    />
  )
}

function DashboardView({
  live,
  workload,
  statusMix,
  completion,
  presenceDate,
  presence,
  loading = false,
  error = null,
  onRefresh,
}: {
  live: boolean
  workload: WorkloadRow[]
  statusMix: Record<(typeof MIX_ORDER)[number], number>
  completion: { rate: number; completedInLast7Days: number; totalTasks: number }
  presenceDate: string
  presence: PresenceRow[]
  loading?: boolean
  error?: string | null
  onRefresh?: () => void
}) {
  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-heading text-2xl font-medium">Team dashboard</h1>
          <p className="text-sm text-muted-foreground">
            {live
              ? 'Live team aggregates. Reload this page or refresh to recompute. Members cannot open this route; a member JWT still gets 403.'
              : 'Mock aggregates from in-memory tasks and standups. This route is hidden from members; hiding is not authorization.'}
          </p>
        </div>
        {onRefresh ? (
          <Button type="button" variant="outline" onClick={onRefresh} disabled={loading}>
            Refresh
          </Button>
        ) : null}
      </div>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
      {loading ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Per-member workload</CardTitle>
            <CardDescription>Counts of To do and In progress by assignee.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {workload.length === 0 ? (
              <p className="text-muted-foreground">No team members.</p>
            ) : (
              workload.map((user) => (
                <div key={user.key} className="flex justify-between gap-4">
                  <span>{user.displayName}</span>
                  <span className="text-muted-foreground">
                    {user.toDo} to do · {user.inProgress} in progress
                  </span>
                </div>
              ))
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Team status mix</CardTitle>
            <CardDescription>All team tasks.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {MIX_ORDER.map((status) => (
              <div key={status} className="flex items-center justify-between gap-4">
                <StatusBadge status={status} />
                <span>{statusMix[status]}</span>
              </div>
            ))}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>7-day completion</CardTitle>
            <CardDescription>
              Share of all team tasks whose last completion time is within 7 days.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-medium">{Math.round(completion.rate * 100)}%</p>
            <p className="text-sm text-muted-foreground">
              {completion.completedInLast7Days} of {completion.totalTasks} tasks completed in the last 7 days.
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Standup presence</CardTitle>
            <CardDescription>Today UTC ({presenceDate}). Lead included as a team user.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {presence.length === 0 ? (
              <p className="text-muted-foreground">No team members.</p>
            ) : (
              presence.map((user) => (
                <div key={user.key} className="flex justify-between gap-4">
                  <span>{user.displayName}</span>
                  <span className={user.submitted ? '' : 'text-destructive'}>
                    {user.submitted ? 'Submitted' : 'Missing'}
                  </span>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
