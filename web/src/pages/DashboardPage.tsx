import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { utcDateString } from '@/lib/dates'
import { TASK_STATUSES } from '@/types/domain'
import { useMockStore } from '@/mock/store'
import { userName } from '@/lib/users'
import { StatusBadge } from '@/components/StatusBadge'
import { useState } from 'react'

export function DashboardPage() {
  const { users, tasks, standups } = useMockStore()
  const today = utcDateString()
  const [now] = useState(() => Date.now())
  const weekAgo = now - 7 * 24 * 60 * 60 * 1000

  const mix = {
    TO_DO: tasks.filter((task) => task.status === TASK_STATUSES.TO_DO).length,
    IN_PROGRESS: tasks.filter((task) => task.status === TASK_STATUSES.IN_PROGRESS).length,
    COMPLETED: tasks.filter((task) => task.status === TASK_STATUSES.COMPLETED).length,
  }

  const completedRecently = tasks.filter(
    (task) => task.completedAt && Date.parse(task.completedAt) >= weekAgo,
  ).length
  const completionRate = tasks.length === 0 ? 0 : completedRecently / tasks.length

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="font-heading text-2xl font-medium">Team dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Mock aggregates from in-memory tasks and standups. Reload the page to recompute. This
          route is hidden from members; hiding is not authorization.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Per-member workload</CardTitle>
            <CardDescription>Counts of To do and In progress by assignee.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {users.map((user) => {
              const assigned = tasks.filter((task) => task.assigneeId === user.id)
              const todo = assigned.filter((task) => task.status === TASK_STATUSES.TO_DO).length
              const doing = assigned.filter(
                (task) => task.status === TASK_STATUSES.IN_PROGRESS,
              ).length
              return (
                <div key={user.id} className="flex justify-between gap-4">
                  <span>{user.displayName}</span>
                  <span className="text-muted-foreground">
                    {todo} to do · {doing} in progress
                  </span>
                </div>
              )
            })}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Team status mix</CardTitle>
            <CardDescription>All team tasks.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {([TASK_STATUSES.TO_DO, TASK_STATUSES.IN_PROGRESS, TASK_STATUSES.COMPLETED] as const).map(
              (status) => (
                <div key={status} className="flex items-center justify-between gap-4">
                  <StatusBadge status={status} />
                  <span>{mix[status]}</span>
                </div>
              ),
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>7-day completion</CardTitle>
            <CardDescription>
              Share of all tasks whose last completion time is within 7 days.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-medium">{Math.round(completionRate * 100)}%</p>
            <p className="text-sm text-muted-foreground">
              {completedRecently} of {tasks.length} tasks completed in the last 7 days.
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Standup presence</CardTitle>
            <CardDescription>Today UTC ({today}). Lead included as a team user.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {users.map((user) => {
              const submitted = standups.some(
                (entry) => entry.userId === user.id && entry.standupDate === today,
              )
              return (
                <div key={user.id} className="flex justify-between gap-4">
                  <span>{userName(users, user.id)}</span>
                  <span className={submitted ? '' : 'text-destructive'}>
                    {submitted ? 'Submitted' : 'Missing'}
                  </span>
                </div>
              )
            })}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
