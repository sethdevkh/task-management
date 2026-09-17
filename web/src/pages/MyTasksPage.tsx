import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { createTask, deleteTask, listMyTasks, updateTask, type ApiTask } from '@/api/tasks'
import { useSession } from '@/auth/session'
import { StatusSelect } from '@/components/StatusSelect'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
import { userName } from '@/lib/users'
import { useMockStore } from '@/mock/store'
import { ROLES, type Task, type TaskStatus, type User } from '@/types/domain'
import { ApiError } from '@/api/client'

type Row = {
  id: string
  title: string
  description: string
  status: TaskStatus
  assigneeName: string
  dueDate: string | null
  canDelete: boolean
}

function toRowFromApi(task: ApiTask): Row {
  return {
    id: String(task.id),
    title: task.title,
    description: task.description,
    status: task.status,
    assigneeName: task.assigneeDisplayName,
    dueDate: task.dueDate,
    canDelete: task.canDelete,
  }
}

function toRowFromMock(task: Task, users: User[], userId: string, isLead: boolean): Row {
  return {
    id: task.id,
    title: task.title,
    description: task.description,
    status: task.status,
    assigneeName: userName(users, task.assigneeId),
    dueDate: task.dueDate,
    canDelete: isLead || (task.creatorId === userId && task.assigneeId === userId),
  }
}

export function MyTasksPage() {
  return AUTH_MODE === 'live' ? <LiveMyTasksPage /> : <MockMyTasksPage />
}

function LiveMyTasksPage() {
  const { session } = useSession()
  const location = useLocation()
  const denied = (location.state as { denied?: string } | null)?.denied
  const [tasks, setTasks] = useState<Row[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function reload() {
    const next = await listMyTasks()
    setTasks(next.map(toRowFromApi))
  }

  useEffect(() => {
    let cancelled = false
    listMyTasks()
      .then((next) => {
        if (!cancelled) {
          setTasks(next.map(toRowFromApi))
          setError(null)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled && !(err instanceof ApiError && err.status === 401)) {
          setError(err instanceof Error ? err.message : 'Could not load tasks')
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
    <MyTasksView
      denied={denied}
      live
      tasks={tasks}
      loading={loading}
      error={error}
      onCreate={async (input) => {
        await createTask(input)
        await reload()
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

function MockMyTasksPage() {
  const { session } = useSession()
  const { tasks, users, createTask: createMock, updateTaskStatus, deleteTask: removeMock } = useMockStore()
  const location = useLocation()
  const denied = (location.state as { denied?: string } | null)?.denied

  if (!session) {
    return null
  }

  const userId = session.userId
  const isLead = session.role === ROLES.TEAM_LEAD
  const mine = tasks
    .filter((task) => task.assigneeId === userId || task.creatorId === userId)
    .map((task) => toRowFromMock(task, users, userId, isLead))

  return (
    <MyTasksView
      denied={denied}
      live={false}
      tasks={mine}
      onCreate={(input) => {
        createMock({
          title: input.title,
          description: input.description,
          assigneeId: userId,
          creatorId: userId,
          dueDate: input.dueDate || null,
        })
      }}
      onStatus={(id, status) => updateTaskStatus(id, status)}
      onDelete={(id) => removeMock(id)}
    />
  )
}

function MyTasksView({
  denied,
  live,
  tasks,
  loading = false,
  error = null,
  onCreate,
  onStatus,
  onDelete,
}: {
  denied?: string
  live: boolean
  tasks: Row[]
  loading?: boolean
  error?: string | null
  onCreate: (input: { title: string; description: string; dueDate: string }) => Promise<void> | void
  onStatus: (id: string, status: TaskStatus) => Promise<void> | void
  onDelete: (id: string) => Promise<void> | void
}) {
  const { session } = useSession()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [justCreated, setJustCreated] = useState(false)

  if (!session) {
    return null
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = title.trim()
    if (!trimmed) {
      setFormError('Title is required.')
      return
    }
    setSaving(true)
    try {
      await onCreate({
        title: trimmed,
        description: description.trim(),
        dueDate,
      })
      setTitle('')
      setDescription('')
      setDueDate('')
      setFormError(null)
      setJustCreated(true)
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
      {denied === 'lead-only' ? (
        <Alert>
          <AlertTitle>Lead screen hidden</AlertTitle>
          <AlertDescription>
            This UI hid a lead route. Hiding a page is not authorization. A live API must still
            return 403.
          </AlertDescription>
        </Alert>
      ) : null}
      <div>
        <h1 className="font-heading text-2xl font-medium">My Tasks</h1>
        <p className="text-sm text-muted-foreground">
          Tasks you created or are assigned. Assignee is locked to you
          {session.role === ROLES.TEAM_LEAD ? ' on this screen.' : '.'}
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Create task</CardTitle>
          <CardDescription>
            Status defaults to To do. {live ? 'Saved on the API.' : 'Mock data stays in memory.'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-3" onSubmit={submit}>
            <div className="grid gap-2">
              <Label htmlFor="task-title">Title</Label>
              <Input
                id="task-title"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="task-description">Description</Label>
              <Textarea
                id="task-description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="task-due">Due date</Label>
              <Input
                id="task-due"
                type="date"
                value={dueDate}
                onChange={(event) => setDueDate(event.target.value)}
              />
            </div>
            {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
            {justCreated && !formError ? (
              <p className="text-sm text-muted-foreground">
                Task saved.{' '}
                <Link to="/standup" className="font-medium text-foreground underline underline-offset-4">
                  Submit today’s standup
                </Link>
              </p>
            ) : null}
            <Button type="submit" className="w-fit" disabled={saving}>
              Create
            </Button>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>List</CardTitle>
          <CardDescription>
            {loading ? 'Loading…' : `${tasks.length} task${tasks.length === 1 ? '' : 's'}`}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          {!loading && tasks.length === 0 && !error ? (
            <p className="text-sm text-muted-foreground">No tasks yet.</p>
          ) : null}
          {tasks.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Assignee</TableHead>
                  <TableHead>Due</TableHead>
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
                    <TableCell>{task.assigneeName}</TableCell>
                    <TableCell>{task.dueDate ?? '—'}</TableCell>
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
