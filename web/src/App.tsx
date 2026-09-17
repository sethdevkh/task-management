import { Navigate, Route, Routes } from 'react-router-dom'
import { GuestOnly, RequireLead, RequireSession } from '@/auth/guards'
import { SessionProvider } from '@/auth/session'
import { AppShell } from '@/components/layout/AppShell'
import { MockStoreProvider } from '@/mock/store'
import { DashboardPage } from '@/pages/DashboardPage'
import { HomeRedirect } from '@/pages/HomeRedirect'
import { LoginPage } from '@/pages/LoginPage'
import { MyTasksPage } from '@/pages/MyTasksPage'
import { StandupPage } from '@/pages/StandupPage'
import { TeamTasksPage } from '@/pages/TeamTasksPage'

export default function App() {
  return (
    <SessionProvider>
      <MockStoreProvider>
        <Routes>
          <Route element={<AppShell />}>
            <Route
              path="/login"
              element={
                <GuestOnly>
                  <LoginPage />
                </GuestOnly>
              }
            />
            <Route path="/" element={<HomeRedirect />} />
            <Route
              path="/tasks"
              element={
                <RequireSession>
                  <MyTasksPage />
                </RequireSession>
              }
            />
            <Route
              path="/standup"
              element={
                <RequireSession>
                  <StandupPage />
                </RequireSession>
              }
            />
            <Route
              path="/dashboard"
              element={
                <RequireLead>
                  <DashboardPage />
                </RequireLead>
              }
            />
            <Route
              path="/team/tasks"
              element={
                <RequireLead>
                  <TeamTasksPage />
                </RequireLead>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </MockStoreProvider>
    </SessionProvider>
  )
}
