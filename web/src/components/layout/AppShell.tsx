import { Link, NavLink, Outlet } from 'react-router-dom'
import { useSession } from '@/auth/session'
import { Button, buttonVariants } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { ROLES } from '@/types/domain'

function navClassName(isActive: boolean): string {
  return cn(buttonVariants({ variant: isActive ? 'secondary' : 'ghost' }))
}

export function AppShell() {
  const { session, signOut } = useSession()
  const isLead = session?.role === ROLES.TEAM_LEAD

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="border-b">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <div className="flex items-center gap-3">
            <Link to="/" className="font-heading text-base font-medium">
              Task Management
            </Link>
            <Badge variant="outline">Mock UI</Badge>
          </div>
          {session ? (
            <div className="flex items-center gap-2">
              <nav className="hidden items-center gap-1 sm:flex" aria-label="Main">
                {isLead ? (
                  <>
                    <NavLink to="/dashboard" className={({ isActive }) => navClassName(isActive)}>
                      Dashboard
                    </NavLink>
                    <NavLink to="/team/tasks" className={({ isActive }) => navClassName(isActive)}>
                      Team tasks
                    </NavLink>
                  </>
                ) : null}
                <NavLink to="/tasks" className={({ isActive }) => navClassName(isActive)}>
                  My Tasks
                </NavLink>
                <NavLink to="/standup" className={({ isActive }) => navClassName(isActive)}>
                  Today standup
                </NavLink>
              </nav>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline">{session.displayName}</Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuLabel>
                    {session.role === ROLES.TEAM_LEAD ? 'Team lead' : 'Team member'}
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator className="sm:hidden" />
                  {isLead ? (
                    <>
                      <DropdownMenuItem asChild className="sm:hidden">
                        <Link to="/dashboard">Dashboard</Link>
                      </DropdownMenuItem>
                      <DropdownMenuItem asChild className="sm:hidden">
                        <Link to="/team/tasks">Team tasks</Link>
                      </DropdownMenuItem>
                    </>
                  ) : null}
                  <DropdownMenuItem asChild className="sm:hidden">
                    <Link to="/tasks">My Tasks</Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild className="sm:hidden">
                    <Link to="/standup">Today standup</Link>
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onClick={signOut}>Sign out</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">Not signed in</p>
          )}
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
