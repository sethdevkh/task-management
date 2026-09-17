import { createContext, useContext, useMemo, useReducer, type ReactNode } from 'react'
import { utcDateString } from '@/lib/dates'
import { TASK_STATUSES, type Standup, type Task, type TaskStatus, type User } from '@/types/domain'
import { createSeedStandups, createSeedTasks, MOCK_USERS } from '@/mock/seed'

type MockState = {
  tasks: Task[]
  standups: Standup[]
}

type MockAction =
  | { type: 'create-task'; task: Task }
  | { type: 'update-task'; id: string; patch: Partial<Task> }
  | { type: 'delete-task'; id: string }
  | {
      type: 'upsert-standup'
      userId: string
      standupDate: string
      done: string
      doing: string
      blockers: string
    }

function reducer(state: MockState, action: MockAction): MockState {
  switch (action.type) {
    case 'create-task':
      return { ...state, tasks: [action.task, ...state.tasks] }
    case 'update-task':
      return {
        ...state,
        tasks: state.tasks.map((task) =>
          task.id === action.id ? { ...task, ...action.patch } : task,
        ),
      }
    case 'delete-task':
      return { ...state, tasks: state.tasks.filter((task) => task.id !== action.id) }
    case 'upsert-standup': {
      const existing = state.standups.find(
        (entry) => entry.userId === action.userId && entry.standupDate === action.standupDate,
      )
      if (existing) {
        return {
          ...state,
          standups: state.standups.map((entry) =>
            entry.id === existing.id
              ? {
                  ...entry,
                  done: action.done,
                  doing: action.doing,
                  blockers: action.blockers,
                }
              : entry,
          ),
        }
      }
      const created: Standup = {
        id: `standup-${crypto.randomUUID()}`,
        userId: action.userId,
        standupDate: action.standupDate,
        done: action.done,
        doing: action.doing,
        blockers: action.blockers,
      }
      return { ...state, standups: [created, ...state.standups] }
    }
    default:
      return state
  }
}

type MockStoreValue = {
  users: User[]
  tasks: Task[]
  standups: Standup[]
  createTask: (input: {
    title: string
    description: string
    assigneeId: string
    creatorId: string
    dueDate: string | null
  }) => void
  updateTaskStatus: (id: string, status: TaskStatus) => void
  assignTask: (id: string, assigneeId: string) => void
  deleteTask: (id: string) => void
  upsertStandup: (input: {
    userId: string
    done: string
    doing: string
    blockers: string
  }) => void
}

const MockStoreContext = createContext<MockStoreValue | null>(null)

export function MockStoreProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, undefined, () => ({
    tasks: createSeedTasks(),
    standups: createSeedStandups(),
  }))

  const value = useMemo<MockStoreValue>(
    () => ({
      users: MOCK_USERS,
      tasks: state.tasks,
      standups: state.standups,
      createTask: (input) => {
        const now = new Date().toISOString()
        dispatch({
          type: 'create-task',
          task: {
            id: `task-${crypto.randomUUID()}`,
            title: input.title,
            description: input.description,
            status: TASK_STATUSES.TO_DO,
            assigneeId: input.assigneeId,
            creatorId: input.creatorId,
            dueDate: input.dueDate,
            createdAt: now,
            updatedAt: now,
            completedAt: null,
          },
        })
      },
      updateTaskStatus: (id, status) => {
        const now = new Date().toISOString()
        dispatch({
          type: 'update-task',
          id,
          patch: {
            status,
            updatedAt: now,
            completedAt: status === TASK_STATUSES.COMPLETED ? now : null,
          },
        })
      },
      assignTask: (id, assigneeId) => {
        dispatch({
          type: 'update-task',
          id,
          patch: {
            assigneeId,
            updatedAt: new Date().toISOString(),
          },
        })
      },
      deleteTask: (id) => {
        dispatch({ type: 'delete-task', id })
      },
      upsertStandup: (input) => {
        dispatch({
          type: 'upsert-standup',
          userId: input.userId,
          standupDate: utcDateString(),
          done: input.done,
          doing: input.doing,
          blockers: input.blockers,
        })
      },
    }),
    [state.standups, state.tasks],
  )

  return <MockStoreContext.Provider value={value}>{children}</MockStoreContext.Provider>
}

export function useMockStore(): MockStoreValue {
  const value = useContext(MockStoreContext)
  if (!value) {
    throw new Error('useMockStore must be used inside MockStoreProvider')
  }
  return value
}
