import { useState, type FormEvent } from 'react'
import { useLocation } from 'react-router-dom'
import { useSession } from '@/auth/session'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { StatusSelect } from '@/components/StatusSelect'
import { useMockStore } from '@/mock/store'
import { userName } from '@/lib/users'
import { ROLES, type TaskStatus } from '@/types/domain'

export function MyTasksPage() {
  const { session } = useSession()
  const { tasks, users, createTask, updateTaskStatus } = useMockStore()
  const location = useLocation()
  const denied = (location.state as { denied?: string } | null)?.denied
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [error, setError] = useState<string | null>(null)

  if (!session) {
    return null
  }

  const userId = session.userId
  const mine = tasks.filter(
    (task) => task.assigneeId === userId || task.creatorId === userId,
  )

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
      assigneeId: userId,
      creatorId: userId,
      dueDate: dueDate || null,
    })
    setTitle('')
    setDescription('')
    setDueDate('')
    setError(null)
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
          <CardDescription>Status defaults to To do. Mock data stays in memory.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-3" onSubmit={onCreate}>
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
            {error ? <p className="text-sm text-destructive">{error}</p> : null}
            <Button type="submit" className="w-fit">
              Create
            </Button>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>List</CardTitle>
          <CardDescription>{mine.length} task{mine.length === 1 ? '' : 's'}</CardDescription>
        </CardHeader>
        <CardContent>
          {mine.length === 0 ? (
            <p className="text-sm text-muted-foreground">No tasks yet.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Assignee</TableHead>
                  <TableHead>Due</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {mine.map((task) => (
                  <TableRow key={task.id}>
                    <TableCell>
                      <div className="font-medium">{task.title}</div>
                      {task.description ? (
                        <div className="text-muted-foreground">{task.description}</div>
                      ) : null}
                    </TableCell>
                    <TableCell>{userName(users, task.assigneeId)}</TableCell>
                    <TableCell>{task.dueDate ?? '—'}</TableCell>
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
