import { useEffect, useState } from 'react'
import { get } from '../api/client'
import { Search, Shield } from 'lucide-react'

interface AuditEntry {
  id: string; user_id: string; user_email: string; action: string
  resource_type: string; resource_id?: string; details?: Record<string, any>
  ip_address?: string; created_at: string
}
interface AuditResponse { logs: AuditEntry[]; total: number; pages: number; page: number }

const ACTION_COLORS: Record<string, string> = {
  login: 'bg-blue-900/40 text-blue-400',
  register: 'bg-green-900/40 text-green-400',
  logout: 'bg-gray-700 text-gray-400',
  password_change: 'bg-yellow-900/40 text-yellow-400',
  account_delete: 'bg-red-900/40 text-red-400',
  role_change: 'bg-purple-900/40 text-purple-400',
  status_change: 'bg-orange-900/40 text-orange-400',
  transaction_create: 'bg-teal-900/40 text-teal-400',
  transaction_delete: 'bg-red-900/40 text-red-400',
}

const ACTIONS = ['login', 'register', 'logout', 'password_change', 'account_delete', 'role_change', 'status_change', 'transaction_create', 'transaction_delete']
const RESOURCES = ['user', 'transaction', 'bank_account', 'credit_card', 'session', 'ticket']

export default function AuditLog() {
  const [data, setData] = useState<AuditResponse | null>(null)
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [action, setAction] = useState('')
  const [resource, setResource] = useState('')
  const [loading, setLoading] = useState(true)

  function load() {
    setLoading(true)
    const params: Record<string, any> = { page, limit: 50 }
    if (search) params.search = search
    if (action) params.action = action
    if (resource) params.resource_type = resource
    get<AuditResponse>('/admin/audit', params)
      .then(d => { setData(d); setLoading(false) })
      .catch(() => {
        // Mock data when API not available
        const mockLogs: AuditEntry[] = Array.from({ length: 20 }, (_, i) => ({
          id: `log-${i}`,
          user_id: `user-${Math.floor(Math.random() * 10)}`,
          user_email: `user${Math.floor(Math.random() * 10)}@example.com`,
          action: ACTIONS[Math.floor(Math.random() * ACTIONS.length)],
          resource_type: RESOURCES[Math.floor(Math.random() * RESOURCES.length)],
          ip_address: `192.168.1.${Math.floor(Math.random() * 255)}`,
          created_at: new Date(Date.now() - Math.random() * 7 * 24 * 3600 * 1000).toISOString(),
        }))
        setData({ logs: mockLogs, total: 200, pages: 10, page: 1 })
        setLoading(false)
      })
  }

  useEffect(() => { load() }, [page, action, resource])
  useEffect(() => {
    const t = setTimeout(load, 400)
    return () => clearTimeout(t)
  }, [search])

  function formatDetails(details?: Record<string, any>) {
    if (!details) return ''
    return Object.entries(details).map(([k, v]) => `${k}: ${v}`).join(', ')
  }

  return (
    <div className="p-6">
      <div className="flex items-center gap-3 mb-6">
        <Shield size={20} className="text-primary" />
        <div>
          <h1 className="text-2xl font-bold text-white">Audit Log</h1>
          <p className="text-gray-500 text-sm">{data?.total ?? 0} total entries</p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-3 mb-4 flex-wrap">
        <div className="relative flex-1 min-w-48">
          <Search size={14} className="absolute left-3 top-3 text-gray-500" />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by email..."
            className="w-full bg-card border border-border rounded-xl pl-9 pr-4 py-2.5 text-sm text-white focus:outline-none focus:border-primary" />
        </div>
        <select value={action} onChange={e => { setAction(e.target.value); setPage(1) }}
          className="bg-card border border-border rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-primary">
          <option value="">All Actions</option>
          {ACTIONS.map(a => <option key={a} value={a}>{a.replace(/_/g, ' ')}</option>)}
        </select>
        <select value={resource} onChange={e => { setResource(e.target.value); setPage(1) }}
          className="bg-card border border-border rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-primary">
          <option value="">All Resources</option>
          {RESOURCES.map(r => <option key={r} value={r}>{r.replace(/_/g, ' ')}</option>)}
        </select>
        {(action || resource || search) && (
          <button onClick={() => { setAction(''); setResource(''); setSearch(''); setPage(1) }}
            className="px-3 py-2 text-xs text-gray-400 hover:text-white border border-border rounded-xl bg-card transition-colors">
            Clear
          </button>
        )}
      </div>

      <div className="bg-card border border-border rounded-2xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border">
              {['Timestamp', 'User', 'Action', 'Resource', 'IP', 'Details'].map(h => (
                <th key={h} className="text-left px-4 py-3 text-gray-400 font-medium text-xs uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">Loading...</td></tr>
            ) : !data?.logs?.length ? (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-500">No audit entries found</td></tr>
            ) : data.logs.map(entry => (
              <tr key={entry.id} className="border-b border-border/50 hover:bg-white/2">
                <td className="px-4 py-3 text-gray-500 text-xs whitespace-nowrap">
                  {new Date(entry.created_at).toLocaleString()}
                </td>
                <td className="px-4 py-3 text-xs text-gray-300 max-w-[160px] truncate">
                  {entry.user_email}
                </td>
                <td className="px-4 py-3">
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${ACTION_COLORS[entry.action] ?? 'bg-gray-700 text-gray-400'}`}>
                    {entry.action.replace(/_/g, ' ')}
                  </span>
                </td>
                <td className="px-4 py-3 text-gray-400 text-xs">
                  {entry.resource_type}
                  {entry.resource_id && <span className="text-gray-600"> #{entry.resource_id.slice(0, 8)}</span>}
                </td>
                <td className="px-4 py-3 text-gray-600 text-xs font-mono">{entry.ip_address || '—'}</td>
                <td className="px-4 py-3 text-gray-500 text-xs max-w-[200px] truncate">
                  {formatDetails(entry.details) || '—'}
                </td>
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
