import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { get } from '../api/client'
import { Clock, AlertCircle, CheckCircle, ChevronRight, RefreshCw, Ticket } from 'lucide-react'

interface TicketItem {
  id: string; subject: string; description: string; status: string; priority: string
  user_email: string; user_name: string; assigned_to_email?: string
  created_at: string; updated_at: string; message_count: number
}
interface TicketsResponse { tickets: TicketItem[]; total: number; pages: number; page: number }

const STATUS_CONFIG: Record<string, { color: string; icon: typeof Clock; label: string }> = {
  open:        { color: 'bg-yellow-900/40 text-yellow-400 border-yellow-800/50', icon: Clock, label: 'Open' },
  in_progress: { color: 'bg-blue-900/40 text-blue-400 border-blue-800/50', icon: RefreshCw, label: 'In Progress' },
  resolved:    { color: 'bg-green-900/40 text-green-400 border-green-800/50', icon: CheckCircle, label: 'Resolved' },
  closed:      { color: 'bg-gray-800 text-gray-400 border-gray-700', icon: CheckCircle, label: 'Closed' },
}
const PRIORITY_DOT: Record<string, string> = { low: 'text-gray-400', medium: 'text-yellow-400', high: 'text-orange-400', urgent: 'text-red-500' }

export default function TicketQueue() {
  const nav = useNavigate()
  const [data, setData] = useState<TicketsResponse | null>(null)
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('open')
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState({ open: 0, in_progress: 0, resolved: 0, total: 0 })

  function load() {
    setLoading(true)
    const params: Record<string, any> = { page, limit: 25 }
    if (status) params.status = status
    get<TicketsResponse>('/support/tickets', params)
      .then(d => { setData(d); setLoading(false) })
      .catch(() => setLoading(false))
  }

  useEffect(() => {
    // Load stats
    Promise.all([
      get<TicketsResponse>('/support/tickets?status=open&limit=1'),
      get<TicketsResponse>('/support/tickets?status=in_progress&limit=1'),
      get<TicketsResponse>('/support/tickets?limit=1'),
    ]).then(([open, inProg, all]) => {
      setStats({ open: open.total, in_progress: inProg.total, resolved: 0, total: all.total })
    }).catch(() => {})
  }, [])

  useEffect(() => { load() }, [page, status])

  const statCards = [
    { label: 'Open', value: stats.open, color: 'text-yellow-400', bg: 'bg-yellow-900/20' },
    { label: 'In Progress', value: stats.in_progress, color: 'text-blue-400', bg: 'bg-blue-900/20' },
    { label: 'Total Tickets', value: stats.total, color: 'text-primary', bg: 'bg-primary/10' },
  ]

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white flex items-center gap-2">
            <Ticket size={20} className="text-primary" /> Ticket Queue
          </h1>
          <p className="text-gray-500 text-sm mt-1">Manage and resolve customer support tickets</p>
        </div>
        <button onClick={load} className="flex items-center gap-2 px-4 py-2 bg-card border border-border rounded-xl text-sm text-gray-300 hover:text-white transition-colors">
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        {statCards.map(({ label, value, color, bg }) => (
          <div key={label} className="bg-card border border-border rounded-xl p-4">
            <div className="text-xs text-gray-500 uppercase tracking-wider mb-1">{label}</div>
            <div className={`text-2xl font-black ${color}`}>{value}</div>
          </div>
        ))}
      </div>

      {/* Status filter */}
      <div className="flex gap-1 mb-4 bg-surface border border-border rounded-xl p-1 w-fit">
        {[['open', 'Open'], ['in_progress', 'In Progress'], ['resolved', 'Resolved'], ['closed', 'Closed'], ['', 'All']].map(([val, label]) => (
          <button key={val} onClick={() => { setStatus(val); setPage(1) }}
            className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${status === val ? 'bg-primary text-white' : 'text-gray-400 hover:text-white'}`}>
            {label}
          </button>
        ))}
      </div>

      {/* Ticket list */}
      <div className="bg-card border border-border rounded-2xl overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Loading tickets...</div>
        ) : !data?.tickets?.length ? (
          <div className="p-12 text-center">
            <CheckCircle size={40} className="text-green-400 mx-auto mb-3" />
            <p className="text-gray-400">No tickets in this category</p>
          </div>
        ) : (
          <div>
            {data.tickets.map((ticket, idx) => {
              const sc = STATUS_CONFIG[ticket.status] ?? STATUS_CONFIG.open
              const StatusIcon = sc.icon
              const timeSince = Math.floor((Date.now() - new Date(ticket.created_at).getTime()) / 3600000)
              const timeLabel = timeSince < 1 ? 'Just now' : timeSince < 24 ? `${timeSince}h ago` : `${Math.floor(timeSince/24)}d ago`
              return (
                <div key={ticket.id} onClick={() => nav(`/tickets/${ticket.id}`)}
                  className={`flex items-start gap-4 p-4 cursor-pointer hover:bg-white/3 transition-colors ${idx < data.tickets.length - 1 ? 'border-b border-border/50' : ''}`}>
                  <div className={`mt-0.5 p-1.5 rounded-lg border ${sc.color}`}>
                    <StatusIcon size={14} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-white font-medium text-sm truncate">{ticket.subject}</span>
                      {ticket.priority === 'urgent' && (
                        <AlertCircle size={13} className="text-red-500 flex-shrink-0" />
                      )}
                    </div>
                    <p className="text-gray-500 text-xs truncate mb-2">{ticket.description}</p>
                    <div className="flex items-center gap-3 text-xs">
                      <span className="text-gray-400">{ticket.user_name || ticket.user_email}</span>
                      <span className={`font-medium ${PRIORITY_DOT[ticket.priority] ?? ''}`}>● {ticket.priority}</span>
                      {ticket.message_count > 0 && (
                        <span className="text-gray-600">{ticket.message_count} msg{ticket.message_count > 1 ? 's' : ''}</span>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <span className="text-xs text-gray-600">{timeLabel}</span>
                    <ChevronRight size={14} className="text-gray-600" />
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {data && data.pages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <button onClick={() => setPage(p => Math.max(1, p-1))} disabled={page <= 1} className="px-4 py-2 bg-card border border-border rounded-lg text-sm text-gray-300 disabled:opacity-30">← Prev</button>
          <span className="text-sm text-gray-400">Page {page} of {data.pages} ({data.total} total)</span>
          <button onClick={() => setPage(p => Math.min(data.pages, p+1))} disabled={page >= data.pages} className="px-4 py-2 bg-card border border-border rounded-lg text-sm text-gray-300 disabled:opacity-30">Next →</button>
        </div>
      )}
    </div>
  )
}
