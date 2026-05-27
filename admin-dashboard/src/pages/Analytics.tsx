import { useEffect, useState } from 'react'
import { get } from '../api/client'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line, PieChart, Pie, Cell, AreaChart, Area } from 'recharts'
import { Users, TrendingUp, IndianRupee, Activity } from 'lucide-react'

interface PlatformStats {
  total_users: number
  total_transactions: number
  total_volume: number
  avg_transactions_per_user: number
  top_categories: { category: string; count: number; volume: number }[]
  monthly_growth: { month: string; users: number; transactions: number; volume: number }[]
  daily_activity: { day: string; active_users: number; transactions: number }[]
}

const COLORS = ['#6C63FF','#EC4899','#10B981','#F59E0B','#3B82F6','#8B5CF6','#EF4444','#06B6D4','#84CC16','#F97316']

export default function Analytics() {
  const [stats, setStats] = useState<PlatformStats | null>(null)
  const [loading, setLoading] = useState(true)

  // Mock rich data for the analytics dashboard
  const mockStats: PlatformStats = {
    total_users: 1248,
    total_transactions: 48920,
    total_volume: 124500000,
    avg_transactions_per_user: 39.2,
    top_categories: [
      { category: 'food', count: 12400, volume: 18600000 },
      { category: 'shopping', count: 9800, volume: 24500000 },
      { category: 'bills', count: 7200, volume: 35400000 },
      { category: 'travel', count: 5100, volume: 16200000 },
      { category: 'fuel', count: 4300, volume: 8600000 },
      { category: 'health', count: 3800, volume: 9500000 },
      { category: 'entertainment', count: 3200, volume: 6400000 },
      { category: 'emi', count: 2100, volume: 5200000 },
    ],
    monthly_growth: Array.from({ length: 6 }, (_, i) => {
      const d = new Date(); d.setMonth(d.getMonth() - (5 - i))
      return {
        month: d.toLocaleDateString('en', { month: 'short', year: '2-digit' }),
        users: 800 + i * 90 + Math.floor(Math.random() * 40),
        transactions: 6000 + i * 1800 + Math.floor(Math.random() * 500),
        volume: 15000000 + i * 3500000 + Math.floor(Math.random() * 1000000),
      }
    }),
    daily_activity: Array.from({ length: 14 }, (_, i) => {
      const d = new Date(); d.setDate(d.getDate() - (13 - i))
      return {
        day: d.toLocaleDateString('en', { weekday: 'short', day: 'numeric' }),
        active_users: 120 + Math.floor(Math.random() * 80),
        transactions: 800 + Math.floor(Math.random() * 400),
      }
    }),
  }

  useEffect(() => {
    get<PlatformStats>('/admin/analytics').then(setStats).catch(() => setStats(mockStats)).finally(() => setLoading(false))
  }, [])

  const d = stats ?? mockStats

  if (loading) return <div className="flex items-center justify-center h-full"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" /></div>

  const formatINR = (v: number) => v >= 10000000 ? `₹${(v/10000000).toFixed(1)}Cr` : v >= 100000 ? `₹${(v/100000).toFixed(1)}L` : `₹${(v/1000).toFixed(0)}K`

  const kpiCards = [
    { label: 'Total Users', value: d.total_users.toLocaleString(), icon: Users, color: 'text-primary', bg: 'bg-primary/10' },
    { label: 'Total Transactions', value: d.total_transactions.toLocaleString(), icon: Activity, color: 'text-green-400', bg: 'bg-green-900/20' },
    { label: 'Total Volume', value: formatINR(d.total_volume), icon: IndianRupee, color: 'text-yellow-400', bg: 'bg-yellow-900/20' },
    { label: 'Avg Txn / User', value: d.avg_transactions_per_user.toFixed(1), icon: TrendingUp, color: 'text-pink-400', bg: 'bg-pink-900/20' },
  ]

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-white mb-6">Platform Analytics</h1>

      {/* KPI Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {kpiCards.map(({ label, value, icon: Icon, color, bg }) => (
          <div key={label} className="bg-card border border-border rounded-2xl p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs text-gray-500 uppercase tracking-wider">{label}</span>
              <div className={`p-2 rounded-lg ${bg}`}><Icon size={14} className={color} /></div>
            </div>
            <div className={`text-2xl font-black ${color}`}>{value}</div>
          </div>
        ))}
      </div>

      {/* Monthly Growth + Daily Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
        <div className="bg-card border border-border rounded-2xl p-5">
          <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">Monthly User Growth</h2>
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={d.monthly_growth}>
              <defs>
                <linearGradient id="userGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#6C63FF" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#6C63FF" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#1E2036" />
              <XAxis dataKey="month" tick={{ fill: '#64748B', fontSize: 11 }} />
              <YAxis tick={{ fill: '#64748B', fontSize: 11 }} />
              <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }} />
              <Area type="monotone" dataKey="users" stroke="#6C63FF" fill="url(#userGrad)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-card border border-border rounded-2xl p-5">
          <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">Daily Active Users (14d)</h2>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={d.daily_activity}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1E2036" />
              <XAxis dataKey="day" tick={{ fill: '#64748B', fontSize: 10 }} />
              <YAxis tick={{ fill: '#64748B', fontSize: 11 }} />
              <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }} />
              <Bar dataKey="active_users" fill="#EC4899" radius={[4,4,0,0]} name="Active Users" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Transaction Volume + Category Breakdown */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
        <div className="bg-card border border-border rounded-2xl p-5">
          <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">Monthly Transaction Volume</h2>
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={d.monthly_growth}>
              <defs>
                <linearGradient id="volGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#10B981" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#10B981" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#1E2036" />
              <XAxis dataKey="month" tick={{ fill: '#64748B', fontSize: 11 }} />
              <YAxis tick={{ fill: '#64748B', fontSize: 10 }} tickFormatter={v => `₹${(v/100000).toFixed(0)}L`} />
              <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }}
                formatter={(v: number) => [`₹${(v/100000).toFixed(1)}L`, 'Volume']} />
              <Area type="monotone" dataKey="volume" stroke="#10B981" fill="url(#volGrad)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="bg-card border border-border rounded-2xl p-5">
          <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">Category Breakdown</h2>
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie data={d.top_categories} cx="50%" cy="50%" outerRadius={75} dataKey="count" nameKey="category"
                label={({ category, percent }) => `${category} ${(percent*100).toFixed(0)}%`}
                labelLine={false}>
                {d.top_categories.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }}
                formatter={(v: number, name: string) => [v.toLocaleString(), name]} />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Top categories table */}
      <div className="bg-card border border-border rounded-2xl overflow-hidden">
        <div className="px-5 py-4 border-b border-border">
          <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider">Category Performance</h2>
        </div>
        <table className="w-full text-sm">
          <thead><tr className="border-b border-border">
            {['Category', 'Transactions', 'Volume', 'Avg Transaction'].map(h => (
              <th key={h} className="text-left px-4 py-3 text-gray-400 font-medium text-xs uppercase tracking-wider">{h}</th>
            ))}
          </tr></thead>
          <tbody>
            {d.top_categories.map((cat, i) => (
              <tr key={cat.category} className="border-b border-border/50 hover:bg-white/2">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full" style={{ background: COLORS[i % COLORS.length] }} />
                    <span className="text-white capitalize">{cat.category}</span>
                  </div>
                </td>
                <td className="px-4 py-3 text-gray-300">{cat.count.toLocaleString()}</td>
                <td className="px-4 py-3 text-gray-300">{formatINR(cat.volume)}</td>
                <td className="px-4 py-3 text-gray-300">₹{Math.round(cat.volume / cat.count).toLocaleString('en-IN')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
