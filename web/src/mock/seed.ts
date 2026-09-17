import { daysAgoIso, utcDateString } from '@/lib/dates'
import { ROLES, TASK_STATUSES, type Standup, type Task, type User } from '@/types/domain'

export const MOCK_USERS: User[] = [
  { id: 'user-casey', displayName: 'Casey Lead', role: ROLES.TEAM_LEAD },
  { id: 'user-alex', displayName: 'Alex Member', role: ROLES.TEAM_MEMBER },
  { id: 'user-bailey', displayName: 'Bailey Member', role: ROLES.TEAM_MEMBER },
]

export const MOCK_TEAM_NAME = 'Platform'

export function createSeedTasks(): Task[] {
  return [
    {
      id: 'task-1',
      title: 'Draft sprint checklist',
      description: 'Shared list for the next demo.',
      status: TASK_STATUSES.TO_DO,
      assigneeId: 'user-alex',
      creatorId: 'user-casey',
      dueDate: null,
      createdAt: daysAgoIso(5),
      updatedAt: daysAgoIso(5),
      completedAt: null,
    },
    {
      id: 'task-2',
      title: 'Fix standup date display',
      description: 'Show UTC date on the form.',
      status: TASK_STATUSES.IN_PROGRESS,
      assigneeId: 'user-bailey',
      creatorId: 'user-bailey',
      dueDate: utcDateString(),
      createdAt: daysAgoIso(3),
      updatedAt: daysAgoIso(1),
      completedAt: null,
    },
    {
      id: 'task-3',
      title: 'Write mock login copy',
      description: '',
      status: TASK_STATUSES.COMPLETED,
      assigneeId: 'user-alex',
      creatorId: 'user-alex',
      dueDate: null,
      createdAt: daysAgoIso(8),
      updatedAt: daysAgoIso(2),
      completedAt: daysAgoIso(2),
    },
    {
      id: 'task-4',
      title: 'Review dashboard widgets',
      description: 'Workload, mix, completion, presence.',
      status: TASK_STATUSES.TO_DO,
      assigneeId: 'user-casey',
      creatorId: 'user-casey',
      dueDate: null,
      createdAt: daysAgoIso(1),
      updatedAt: daysAgoIso(1),
      completedAt: null,
    },
  ]
}

export function createSeedStandups(): Standup[] {
  return [
    {
      id: 'standup-alex-today',
      userId: 'user-alex',
      standupDate: utcDateString(),
      done: 'Finished mock login copy',
      doing: 'Fixing standup date display',
      blockers: '',
    },
    {
      id: 'standup-bailey-yesterday',
      userId: 'user-bailey',
      standupDate: utcDateString(new Date(Date.now() - 24 * 60 * 60 * 1000)),
      done: 'Started the date display fix',
      doing: 'Still in progress',
      blockers: 'Waiting on copy review',
    },
  ]
}
