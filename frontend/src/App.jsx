import { Navigate, Route, Routes } from 'react-router-dom'
import AppShell from './components/AppShell'
import JobsPage from './pages/JobsPage'
import CompaniesPage from './pages/CompaniesPage'
import PeoplePage from './pages/PeoplePage'
import ProfilePage from './pages/ProfilePage'
import TrackerPage from './pages/TrackerPage'

export default function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/jobs" replace />} />
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="/tracker" element={<TrackerPage />} />
        <Route path="/people" element={<PeoplePage />} />
        <Route path="/companies" element={<CompaniesPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="*" element={<Navigate to="/jobs" replace />} />
      </Routes>
    </AppShell>
  )
}
