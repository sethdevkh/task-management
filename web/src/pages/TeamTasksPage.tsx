import { useMemo, useState, type FormEvent } from 'react'
import { useSession } from '@/auth/session'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
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
import { StatusSelect } from '@/components/StatusSelect'
import { statusLabel } from '@/lib/status'
import { TASK_STATUSES, type TaskStatus } from '@/types/domain'
import { useMockStore } from '@/mock/store'
import { userName } from '@/lib/users'

const ALL = 'ALL'

export function TeamTasksPage() {
  const { session } = useSession()
  const { users, tasks, createTask, updateTaskStatus, assignTask } = useMockStore()
  const [memberFilter, setMemberFilter] = useState(ALL)
  const [statusFilter, setStatusFilter] = useState(ALL)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [assigneeId, setAssigneeId] = useState(users[0]?.id ?? '')
  const [dueDate, setDueDate] = useState('')
  const [error, setError] = useState<string | null>(null)

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

  function onCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = title.trim()
    if (!trimmed) {
      setError('Title is required.')
      return
    }
    createTask({
      title: trimmed,
      description: description.trim(),
      assigneeId,
      creatorId,
      dueDate: dueDate || null,
    })
    setTitle('')
    setDescription('')
    setDueDate('')
    setError(null)
  }

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="font-heading text-2xl font-medium">Team tasks</h1>
        <p className="text-sm text-muted-foreground">
          Lead-only mock screen. Members cannot open this route in the UI. That is not API
          authorization.
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Create and assign</CardTitle>
          <CardDescription>Assign to any seeded teammate, including yourself.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-3" onSubmit={onCreate}>
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
                <Select value={assigneeId} onValueChange={setAssigneeId}>
                  <SelectTrigger id="lead-assignee" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {users.map((user) => (
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
            {error ? <p className="text-sm text-destructive">{error}</p> : null}
            <Button type="submit" className="w-fit">
              Create
            </Button>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Team list</CardTitle>
          <CardDescription>Filter by member and status, then reassign.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <div className="flex flex-wrap gap-3">
            <div className="grid gap-2">
              <Label>Member</Label>
              <Select value={memberFilter} onValueChange={setMemberFilter}>
                <SelectTrigger className="w-48">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All members</SelectItem>
                  {users.map((user) => (
                    <SelectItem key={user.id} value={user.id}>
                      {user.displayName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>Status</Label>
              <Select value={statusFilter} onValueChange={setStatusFilter}>
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
          {filtered.length === 0 ? (
            <p className="text-sm text-muted-foreground">No tasks match the filters.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Assignee</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map((task) => (
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
                        onValueChange={(next) => assignTask(task.id, next)}
                      >
                        <SelectTrigger className="w-44">
                          <SelectValue>{userName(users, task.assigneeId)}</SelectValue>
                        </SelectTrigger>
                        <SelectContent>
                          {users.map((user) => (
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
                        onChange={(status: TaskStatus) => updateTaskStatus(task.id, status)}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
