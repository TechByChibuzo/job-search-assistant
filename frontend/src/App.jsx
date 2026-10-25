import { Navigate, Route, Routes } from 'react-router-dom'
import Header from './components/Header'
import JobsPage from './pages/JobsPage'
import CompaniesPage from './pages/CompaniesPage'

export default function App() {
  return (
    <div className="min-h-dvh">
      <Header />
      <main className="mx-auto max-w-7xl px-4 pb-16 sm:px-6">
        <Routes>
          <Route path="/" element={<Navigate to="/jobs" replace />} />
          <Route path="/jobs" element={<JobsPage />} />
          <Route path="/companies" element={<CompaniesPage />} />
          <Route path="*" element={<Navigate to="/jobs" replace />} />
        </Routes>
      </main>
    </div>
  )
}
