import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError } from '@/api/client'
import {
  assignTask as assignLiveTask,
  createTask,
  deleteTask,
  listLeadTasks,
  updateTask,
  type ApiTeamMember,
  type ApiTask,
} from '@/api/tasks'
import { useSession } from '@/auth/session'
import { StatusSelect } from '@/components/StatusSelect'
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { AUTH_MODE } from '@/config/auth-mode'
import { statusLabel } from '@/lib/status'
import { userName } from '@/lib/users'
import { useMockStore } from '@/mock/store'
import { TASK_STATUSES, type Task, type TaskStatus } from '@/types/domain'

const ALL = 'ALL'

type Row = {
  id: string
  title: string
  description: string
  status: TaskStatus
  assigneeId: string
  assigneeName: string
  canDelete: boolean
}

type MemberOption = { id: string; displayName: string }

function toRowFromApi(task: ApiTask): Row {
  return {
    id: String(task.id),
    title: task.title,
    description: task.description,
    status: task.status,
    assigneeId: String(task.assigneeId),
    assigneeName: task.assigneeDisplayName,
    canDelete: task.canDelete,
  }
}

export function TeamTasksPage() {
  return AUTH_MODE === 'live' ? <LiveTeamTasksPage /> : <MockTeamTasksPage />
}

function LiveTeamTasksPage() {
  const { session } = useSession()
  const [memberFilter, setMemberFilter] = useState(ALL)
  const [statusFilter, setStatusFilter] = useState(ALL)
  const [tasks, setTasks] = useState<Row[]>([])
  const [members, setMembers] = useState<MemberOption[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function reload(nextMember = memberFilter, nextStatus = statusFilter) {
    const body = await listLeadTasks({
      assigneeId: nextMember === ALL ? undefined : Number(nextMember),
      status: nextStatus === ALL ? undefined : (nextStatus as TaskStatus),
    })
    setTasks(body.tasks.map(toRowFromApi))
    setMembers(body.members.map((member: ApiTeamMember) => ({
      id: String(member.id),
      displayName: member.displayName,
    })))
  }

  useEffect(() => {
    let cancelled = false
    listLeadTasks()
      .then((body) => {
        if (cancelled) {
          return
        }
        setTasks(body.tasks.map(toRowFromApi))
        const nextMembers = body.members.map((member) => ({
          id: String(member.id),
          displayName: member.displayName,
        }))
        setMembers(nextMembers)
        setError(null)
      })
      .catch((err: unknown) => {
        if (!cancelled && !(err instanceof ApiError && err.status === 401)) {
          setError(err instanceof Error ? err.message : 'Could not load team tasks')
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
    <TeamTasksView
      live
      tasks={tasks}
      members={members}
      memberFilter={memberFilter}
      statusFilter={statusFilter}
      loading={loading}
      error={error}
      onMemberFilter={async (value) => {
        setMemberFilter(value)
        await reload(value, statusFilter)
      }}
      onStatusFilter={async (value) => {
        setStatusFilter(value)
        await reload(memberFilter, value)
      }}
      onCreate={async (input) => {
        await createTask({
          title: input.title,
          description: input.description,
          dueDate: input.dueDate,
          assigneeId: Number(input.assigneeId),
        })
        await reload()
      }}
      onAssign={async (id, assigneeId) => {
        const updated = await assignLiveTask(Number(id), Number(assigneeId))
        setTasks((current) => current.map((task) => (task.id === id ? toRowFromApi(updated) : task)))
      }}
      onStatus={async (id, status) => {
        const updated = await updateTask(Number(id), { status })
        setTasks((current) => current.map((task) => (task.id === id ? toRowFromApi(updated) : task)))
      }}
      onDelete={async (id) => {
        await deleteTask(Number(id))
        setTasks((current) => current.filter((task) => task.id !== id))
      }}
    />
  )
}

function MockTeamTasksPage() {
  const { session } = useSession()
  const { users, tasks, createTask: createMock, updateTaskStatus, assignTask, deleteTask: removeMock } =
    useMockStore()
  const [memberFilter, setMemberFilter] = useState(ALL)
  const [statusFilter, setStatusFilter] = useState(ALL)

  const filtered = useMemo(() => {
    return tasks.filter((task) => {
      const memberOk = memberFilter === ALL || task.assigneeId === memberFilter
      const statusOk = statusFilter === ALL || task.status === statusFilter
      return memberOk && statusOk
    })
  }, [memberFilter, statusFilter, tasks])

  if (!session) {
    return null
  }

  const creatorId = session.userId
  const rows: Row[] = filtered.map((task: Task) => ({
    id: task.id,
    title: task.title,
    description: task.description,
    status: task.status,
    assigneeId: task.assigneeId,
    assigneeName: userName(users, task.assigneeId),
    canDelete: true,
  }))
  const members = users.map((user) => ({ id: user.id, displayName: user.displayName }))

  return (
    <TeamTasksView
      live={false}
      tasks={rows}
      members={members}
      memberFilter={memberFilter}
      statusFilter={statusFilter}
      onMemberFilter={(value) => setMemberFilter(value)}
      onStatusFilter={(value) => setStatusFilter(value)}
      onCreate={(input) => {
        createMock({
          title: input.title,
          description: input.description,
          assigneeId: input.assigneeId,
          creatorId,
          dueDate: input.dueDate || null,
        })
      }}
      onAssign={(id, assigneeId) => assignTask(id, assigneeId)}
      onStatus={(id, status) => updateTaskStatus(id, status)}
      onDelete={(id) => removeMock(id)}
    />
  )
}

function TeamTasksView({
  live,
  tasks,
  members,
  memberFilter,
  statusFilter,
  loading = false,
  error = null,
  onMemberFilter,
  onStatusFilter,
  onCreate,
  onAssign,
  onStatus,
  onDelete,
}: {
  live: boolean
  tasks: Row[]
  members: MemberOption[]
  memberFilter: string
  statusFilter: string
  loading?: boolean
  error?: string | null
  onMemberFilter: (value: string) => Promise<void> | void
  onStatusFilter: (value: string) => Promise<void> | void
  onCreate: (input: {
    title: string
    description: string
    assigneeId: string
    dueDate: string
  }) => Promise<void> | void
  onAssign: (id: string, assigneeId: string) => Promise<void> | void
  onStatus: (id: string, status: TaskStatus) => Promise<void> | void
  onDelete: (id: string) => Promise<void> | void
}) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [assigneeId, setAssigneeId] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const selectedAssignee = assigneeId || members[0]?.id || ''

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = title.trim()
    if (!trimmed) {
      setFormError('Title is required.')
      return
    }
    if (!selectedAssignee) {
      setFormError('Assignee is required.')
      return
    }
    setSaving(true)
    try {
      await onCreate({
        title: trimmed,
        description: description.trim(),
        assigneeId: selectedAssignee,
        dueDate,
      })
      setTitle('')
      setDescription('')
      setDueDate('')
      setFormError(null)
    } catch (err: unknown) {
      if (!(err instanceof ApiError && err.status === 401)) {
        setFormError(err instanceof Error ? err.message : 'Could not create task')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="font-heading text-2xl font-medium">Team tasks</h1>
        <p className="text-sm text-muted-foreground">
          {live
            ? 'Lead-only API. Members who call this endpoint get 403; hiding the route is not authorization.'
            : 'Lead-only mock screen. Members cannot open this route in the UI. That is not API authorization.'}
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Create and assign</CardTitle>
          <CardDescription>Assign to any teammate, including yourself.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-3" onSubmit={submit}>
            <div className="grid gap-2">
              <Label htmlFor="lead-title">Title</Label>
              <Input
                id="lead-title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="lead-description">Description</Label>
              <Textarea
                id="lead-description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>
            <div className="grid gap-2 sm:grid-cols-2 sm:gap-4">
              <div className="grid gap-2">
                <Label htmlFor="lead-assignee">Assignee</Label>
                <Select value={selectedAssignee} onValueChange={setAssigneeId} disabled={members.length === 0}>
                  <SelectTrigger id="lead-assignee" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {members.map((user) => (
                      <SelectItem key={user.id} value={user.id}>
                        {user.displayName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="lead-due">Due date</Label>
                <Input
                  id="lead-due"
                  type="date"
                  value={dueDate}
                  onChange={(event) => setDueDate(event.target.value)}
                />
              </div>
            </div>
            {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
            <Button type="submit" className="w-fit" disabled={saving}>
              Create
            </Button>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Team list</CardTitle>
          <CardDescription>
            {live ? 'Filter on the API by member and status, then reassign.' : 'Filter by member and status, then reassign.'}
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <div className="flex flex-wrap gap-3">
            <div className="grid gap-2">
              <Label>Member</Label>
              <Select value={memberFilter} onValueChange={(value) => void onMemberFilter(value)}>
                <SelectTrigger className="w-48">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All members</SelectItem>
                  {members.map((user) => (
                    <SelectItem key={user.id} value={user.id}>
                      {user.displayName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>Status</Label>
              <Select value={statusFilter} onValueChange={(value) => void onStatusFilter(value)}>
                <SelectTrigger className="w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All statuses</SelectItem>
                  <SelectItem value={TASK_STATUSES.TO_DO}>{statusLabel(TASK_STATUSES.TO_DO)}</SelectItem>
                  <SelectItem value={TASK_STATUSES.IN_PROGRESS}>
                    {statusLabel(TASK_STATUSES.IN_PROGRESS)}
                  </SelectItem>
                  <SelectItem value={TASK_STATUSES.COMPLETED}>
                    {statusLabel(TASK_STATUSES.COMPLETED)}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          {loading ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
          {!loading && tasks.length === 0 && !error ? (
            <p className="text-sm text-muted-foreground">No tasks match the filters.</p>
          ) : null}
          {tasks.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Assignee</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-24"> </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tasks.map((task) => (
                  <TableRow key={task.id}>
                    <TableCell>
                      <div className="font-medium">{task.title}</div>
                      {task.description ? (
                        <div className="text-muted-foreground">{task.description}</div>
                      ) : null}
                    </TableCell>
                    <TableCell>
                      <Select
                        value={task.assigneeId}
                        onValueChange={(next) => {
                          void onAssign(task.id, next)
                        }}
                      >
                        <SelectTrigger className="w-44">
                          <SelectValue>{task.assigneeName}</SelectValue>
                        </SelectTrigger>
                        <SelectContent>
                          {members.map((user) => (
                            <SelectItem key={user.id} value={user.id}>
                              {user.displayName}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell>
                      <StatusSelect
                        value={task.status}
                        onChange={(status: TaskStatus) => {
                          void onStatus(task.id, status)
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      {task.canDelete ? (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            void onDelete(task.id)
                          }}
                        >
                          Delete
                        </Button>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : null}
        </CardContent>
      </Card>
    </div>
  )
}
