import { NavLink } from 'react-router-dom'
import { Building2, Inbox, KanbanSquare, Monitor, Moon, Sun, Users } from 'lucide-react'
import { useCompanies, useContacts, useDue, useSummary } from '../api/hooks'
import { useTheme } from '../lib/theme'
import SyncPanel from './SyncPanel'

function Brand({ className = '' }) {
  return (
    <span className={`flex items-center gap-2.5 font-semibold tracking-tight ${className}`}>
      <img src="/favicon.svg" alt="" className="size-7" />
      <span className="leading-tight whitespace-nowrap">Job Search Assistant</span>
    </span>
  )
}

function RailLink({ to, icon: Icon, label, count, alert = false }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
          isActive ? 'bg-rail-rule text-rail-ink' : 'text-rail-dim hover:bg-rail-rule/60 hover:text-rail-ink'
        }`
      }
    >
      <Icon className="size-4" aria-hidden />
      <span className="flex-1">{label}</span>
      {count > 0 &&
        (alert ? (
          <span className="rounded-full bg-highlight px-1.5 text-xs font-semibold text-highlight-ink" aria-label={`${count} due`}>
            {count}
          </span>
        ) : (
          <span className="text-xs text-rail-dim">{count.toLocaleString()}</span>
        ))}
    </NavLink>
  )
}

const THEMES = [
  { value: 'system', icon: Monitor, label: 'Match the system' },
  { value: 'light', icon: Sun, label: 'Light' },
  { value: 'dark', icon: Moon, label: 'Dark' },
]

function ThemeSwitch() {
  const [theme, setTheme] = useTheme()
  return (
    <div className="flex rounded-lg border border-rail-rule p-0.5" role="radiogroup" aria-label="Theme">
      {THEMES.map(({ value, icon: Icon, label }) => (
        <button
          key={value}
          type="button"
          role="radio"
          aria-checked={theme === value}
          title={label}
          aria-label={label}
          onClick={() => setTheme(value)}
          className={`flex flex-1 justify-center rounded-md py-1.5 ${
            theme === value ? 'bg-rail-rule text-rail-ink' : 'text-rail-dim hover:text-rail-ink'
          }`}
        >
          <Icon className="size-3.5" />
        </button>
      ))}
    </div>
  )
}

function MobileTab({ to, children }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `rounded-md px-3 py-1.5 text-sm font-medium ${isActive ? 'bg-ink text-paper' : 'text-slate hover:text-ink'}`
      }
    >
      {children}
    </NavLink>
  )
}

export default function AppShell({ children }) {
  const summary = useSummary()
  const companies = useCompanies()
  const due = useDue()
  const people = useContacts()

  return (
    <div className="min-h-dvh lg:pl-64">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col bg-rail p-4 text-rail-ink lg:flex">
        <Brand className="px-2 pt-1 text-[15px]" />
        <nav className="mt-8 flex flex-col gap-1" aria-label="Main">
          <RailLink to="/jobs" icon={Inbox} label="Inbox" count={summary.data?.newJobs ?? 0} />
          <RailLink to="/tracker" icon={KanbanSquare} label="Tracker" count={due.data?.length ?? 0} alert />
          <RailLink to="/people" icon={Users} label="People" count={people.data?.length ?? 0} />
          <RailLink to="/companies" icon={Building2} label="Companies" count={companies.data?.length ?? 0} />
        </nav>
        <div className="mt-auto flex flex-col gap-3">
          <SyncPanel />
          <ThemeSwitch />
        </div>
      </aside>

      <header className="sticky top-0 z-30 border-b border-rule bg-paper/90 backdrop-blur lg:hidden">
        <div className="flex items-center gap-3 px-4 py-2.5">
          <span className="flex items-center gap-2 text-[15px] font-semibold tracking-tight">
            <img src="/favicon.svg" alt="" className="size-6" />
            Job Search Assistant
          </span>
          <div className="ml-auto">
            <SyncPanel compact />
          </div>
        </div>
        <nav className="scroll-quiet flex gap-1 overflow-x-auto px-3 pb-2" aria-label="Main">
          <MobileTab to="/jobs">Inbox</MobileTab>
          <MobileTab to="/tracker">
            Tracker{due.data?.length > 0 && <span className="ml-1.5 rounded-full bg-highlight px-1.5 text-xs text-highlight-ink">{due.data.length}</span>}
          </MobileTab>
          <MobileTab to="/people">People</MobileTab>
          <MobileTab to="/companies">Companies</MobileTab>
        </nav>
      </header>

      <main className="mx-auto max-w-[1400px] px-4 pb-16 sm:px-6 lg:px-8">{children}</main>
    </div>
  )
}
