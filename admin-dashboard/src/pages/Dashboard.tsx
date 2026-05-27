import { useEffect, useState } from 'react'
import { get } from '../api/client'
import { Users, Receipt, TrendingUp, Ticket } from 'lucide-react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line, PieChart, Pie, Cell } from 'recharts'

interface Stats {
  total_users: number
  transactions_today: number
  total_transactions: number
  active_users_today: number
  open_tickets: number
  in_progress_tickets: number
}

const COLORS = ['#6C63FF','#EC4899','#10B981','#F59E0B','#3B82F6','#8B5CF6','#EF4444','#06B6D4']

export default function Dashboard() {
  const [stats, setStats] = useState<Stats | null>(null)
  const [loading, setLoading] = useState(true)

  // Mock trend data
  const trendData = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(); d.setDate(d.getDate() - (6 - i))
    return { day: d.toLocaleDateString('en', { weekday: 'short' }), transactions: Math.floor(Math.random() * 200) + 50, users: Math.floor(Math.random() * 30) + 5 }
  })

  const categoryData = [
    { name: 'Food', value: 28 }, { name: 'Shopping', value: 22 }, { name: 'Bills', value: 18 },
    { name: 'Travel', value: 12 }, { name: 'Health', value: 10 }, { name: 'Other', value: 10 }
  ]

  useEffect(() => {
    get<Stats>('/admin/stats').then(s => { setStats(s); setLoading(false) }).catch(() => setLoading(false))
  }, [])

  if (loading) return <div className="flex items-center justify-center h-full"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" /></div>

  const statCards = [
    { label: 'Total Users',     value: stats?.total_users ?? 0,          icon: Users,    color: 'text-primary' },
    { label: 'Transactions Today', value: stats?.transactions_today ?? 0, icon: Receipt,  color: 'text-green-400' },
    { label: 'Active Today',    value: stats?.active_users_today ?? 0,    icon: TrendingUp, color: 'text-yellow-400' },
    { label: 'Open Tickets',    value: stats?.open_tickets ?? 0,          icon: Ticket,   color: 'text-red-400' },
  ]

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-white mb-6">Dashboard</h1>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {statCards.map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="bg-card border border-border rounded-2xl p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm text-gray-400">{label}</span>
              <Icon size={16} className={color} />
            </div>
            <div className={`text-3xl font-black ${color}`}>{value.toLocaleString()}</div>
          </div>
        ))}
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
        <div className="bg-card border border-border rounded-2xl p-5">
          <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">Daily Transactions (Last 7 Days)</h2>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={trendData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1E2036" />
              <XAxis dataKey="day" tick={{ fill: '#64748B', fontSize: 12 }} />
              <YAxis tick={{ fill: '#64748B', fontSize: 12 }} />
              <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }} />
              <Bar dataKey="transactions" fill="#6C63FF" radius={[4,4,0,0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-card border border-border rounded-2xl p-5">
          <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">Transaction Categories</h2>
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie data={categoryData} cx="50%" cy="50%" outerRadius={80} dataKey="value" label={({ name, value }) => `${name} ${value}%`}>
                {categoryData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }} />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* User signups trend */}
      <div className="bg-card border border-border rounded-2xl p-5">
        <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">User Signups (Last 7 Days)</h2>
        <ResponsiveContainer width="100%" height={150}>
          <LineChart data={trendData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1E2036" />
            <XAxis dataKey="day" tick={{ fill: '#64748B', fontSize: 12 }} />
            <YAxis tick={{ fill: '#64748B', fontSize: 12 }} />
            <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }} />
            <Line type="monotone" dataKey="users" stroke="#EC4899" strokeWidth={2} dot={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}
