import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { get, put } from '../api/client'
import { Search, ChevronRight, CheckCircle, XCircle } from 'lucide-react'

interface User { id: string; email: string; name: string; role: string; is_active: boolean; created_at: string; tx_count: number }
interface UsersResponse { users: User[]; total: number; page: number; pages: number }

export default function Users() {
  const nav = useNavigate()
  const [data, setData] = useState<UsersResponse | null>(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)

  function load() {
    setLoading(true)
    get<UsersResponse>('/admin/users', { page, search: search || undefined, limit: 20 })
      .then(d => { setData(d); setLoading(false) })
      .catch(() => setLoading(false))
  }

  useEffect(() => { load() }, [page])
  useEffect(() => { setPage(1); const t = setTimeout(load, 400); return () => clearTimeout(t) }, [search])

  async function toggleStatus(user: User) {
    await put(`/admin/users/${user.id}/status`, { is_active: !user.is_active })
    load()
  }

  const ROLE_COLORS: Record<string, string> = { admin: 'bg-purple-900/40 text-purple-300', support: 'bg-blue-900/40 text-blue-300', user: 'bg-green-900/40 text-green-300' }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-white">Users</h1>
        <span className="text-gray-400 text-sm">{data?.total ?? 0} total</span>
      </div>

      <div className="relative mb-4">
        <Search size={16} className="absolute left-3 top-3 text-gray-500" />
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by email or name..."
          className="w-full bg-card border border-border rounded-xl pl-9 pr-4 py-2.5 text-sm text-white focus:outline-none focus:border-primary" />
      </div>

      <div className="bg-card border border-border rounded-2xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border">
              {['Email', 'Name', 'Role', 'Transactions', 'Joined', 'Status', ''].map(h => (
                <th key={h} className="text-left px-4 py-3 text-gray-400 font-medium text-xs uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-500">Loading...</td></tr>
            ) : data?.users.map(user => (
              <tr key={user.id} className="border-b border-border/50 hover:bg-white/2 cursor-pointer" onClick={() => nav(`/users/${user.id}`)}>
                <td className="px-4 py-3 text-white">{user.email}</td>
                <td className="px-4 py-3 text-gray-300">{user.name || '—'}</td>
                <td className="px-4 py-3"><span className={`text-xs px-2 py-1 rounded-full font-medium ${ROLE_COLORS[user.role] ?? ''}`}>{user.role}</span></td>
                <td className="px-4 py-3 text-gray-300">{user.tx_count}</td>
                <td className="px-4 py-3 text-gray-500 text-xs">{new Date(user.created_at).toLocaleDateString()}</td>
                <td className="px-4 py-3" onClick={e => { e.stopPropagation(); toggleStatus(user) }}>
                  {user.is_active
                    ? <CheckCircle size={16} className="text-green-400" />
                    : <XCircle size={16} className="text-red-400" />}
                </td>
                <td className="px-4 py-3"><ChevronRight size={14} className="text-gray-600" /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {data && data.pages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <button onClick={() => setPage(p => Math.max(1, p-1))} disabled={page <= 1} className="px-4 py-2 bg-card border border-border rounded-lg text-sm text-gray-300 disabled:opacity-30">← Prev</button>
          <span className="text-sm text-gray-400">Page {page} of {data.pages}</span>
          <button onClick={() => setPage(p => Math.min(data.pages, p+1))} disabled={page >= data.pages} className="px-4 py-2 bg-card border border-border rounded-lg text-sm text-gray-300 disabled:opacity-30">Next →</button>
        </div>
      )}
    </div>
  )
}
