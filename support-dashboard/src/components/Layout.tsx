import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { Ticket, Search, User, LogOut, Headphones } from 'lucide-react'

const NAV = [
  { to: '/', label: 'Ticket Queue', icon: Ticket, exact: true },
  { to: '/users', label: 'User Lookup', icon: Search },
  { to: '/profile', label: 'My Profile', icon: User },
]

export default function Layout() {
  const nav = useNavigate()
  const user = JSON.parse(localStorage.getItem('sw_support_user') || '{}')

  function logout() {
    localStorage.removeItem('sw_support_token')
    localStorage.removeItem('sw_support_user')
    nav('/login')
  }

  return (
    <div className="flex h-screen bg-background overflow-hidden">
      {/* Sidebar */}
      <aside className="w-60 bg-surface border-r border-border flex flex-col flex-shrink-0">
        <div className="p-5 border-b border-border">
          <div className="flex items-center gap-2 mb-1">
            <Headphones size={20} className="text-primary" />
            <div className="text-xl font-black text-primary">SpendWise</div>
          </div>
          <div className="text-xs text-gray-500">Support Dashboard</div>
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
        <div className="p-4 border-t border-border">
          <div className="flex items-center gap-2 mb-2">
            <div className="w-7 h-7 rounded-full bg-primary/20 flex items-center justify-center text-xs font-bold text-primary">
              {(user.email || 'S')[0].toUpperCase()}
            </div>
            <div>
              <div className="text-xs text-white truncate max-w-[130px]">{user.name || 'Support Agent'}</div>
              <div className="text-xs text-gray-600 truncate max-w-[130px]">{user.email}</div>
            </div>
          </div>
          <button onClick={logout} className="flex items-center gap-2 text-xs text-red-400 hover:text-red-300 mt-1">
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
