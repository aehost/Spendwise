import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, Users, Receipt, BarChart3, Ticket, FileText, Settings, LogOut, Cloud } from 'lucide-react'
import DbStatusBanner from './DbStatusBanner'

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, exact: true },
  { to: '/users', label: 'Users', icon: Users },
  { to: '/transactions', label: 'Transactions', icon: Receipt },
  { to: '/analytics', label: 'Analytics', icon: BarChart3 },
  { to: '/tickets', label: 'Tickets', icon: Ticket },
  { to: '/audit', label: 'Audit Log', icon: FileText },
  { to: '/settings', label: 'Settings', icon: Settings },
  { to: '/cloud-setup', label: 'Cloud Setup', icon: Cloud },
]

export default function Layout() {
  const nav = useNavigate()
  const user = JSON.parse(localStorage.getItem('sw_admin_user') || '{}')

  function logout() {
    localStorage.removeItem('sw_admin_token')
    localStorage.removeItem('sw_admin_user')
    nav('/login')
  }

  return (
    <div className="flex h-screen bg-background overflow-hidden">
      {/* Sidebar */}
      <aside className="w-60 bg-surface border-r border-border flex flex-col flex-shrink-0">
        <div className="p-5 border-b border-border">
          <div className="text-2xl font-black text-primary">₹ SpendWise</div>
          <div className="text-xs text-gray-500 mt-1">Admin Dashboard</div>
        </div>
        <nav className="flex-1 p-3 overflow-y-auto">
          {NAV.map(({ to, label, icon: Icon, exact }) => (
            <NavLink
              key={to}
              to={to}
              end={exact}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg mb-1 text-sm font-medium transition-colors ${
                  isActive ? 'bg-primary/15 text-primary' : 'text-gray-400 hover:text-white hover:bg-white/5'
                }`
              }
            >
              <Icon size={16} />
              {label}
            </NavLink>
          ))}
        </nav>
        {/* Service status banner */}
        <DbStatusBanner />
        <div className="p-4 border-t border-border">
          <div className="text-xs text-gray-500 truncate mb-2">{user.email || 'admin'}</div>
          <button onClick={logout} className="flex items-center gap-2 text-xs text-red-400 hover:text-red-300">
            <LogOut size={14} /> Logout
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}
