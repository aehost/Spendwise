import { useEffect, useState } from 'react'
import { get, put, post } from '../api/client'
import { MessageCircle, Clock, CheckCircle, AlertCircle, ChevronRight, X } from 'lucide-react'

interface Ticket {
  id: string; subject: string; description: string; status: string; priority: string
  user_email: string; user_name: string; assigned_to_email?: string
  created_at: string; updated_at: string; message_count: number
}
interface Message { id: string; sender_role: string; message: string; created_at: string; sender_name: string }
interface TicketsResponse { tickets: Ticket[]; total: number; pages: number; page: number }

const STATUS_COLORS: Record<string, string> = {
  open: 'bg-yellow-900/40 text-yellow-400',
  in_progress: 'bg-blue-900/40 text-blue-400',
  resolved: 'bg-green-900/40 text-green-400',
  closed: 'bg-gray-700 text-gray-400',
}
const PRIORITY_COLORS: Record<string, string> = {
  low: 'text-gray-400', medium: 'text-yellow-400', high: 'text-orange-400', urgent: 'text-red-400'
}

export default function Tickets() {
  const [data, setData] = useState<TicketsResponse | null>(null)
  const [page, setPage] = useState(1)
  const [statusFilter, setStatusFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<Ticket | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [reply, setReply] = useState('')
  const [sending, setSending] = useState(false)

  function loadTickets() {
    setLoading(true)
    const params: Record<string, any> = { page, limit: 20 }
    if (statusFilter) params.status = statusFilter
    get<TicketsResponse>('/admin/tickets', params)
      .then(d => { setData(d); setLoading(false) })
      .catch(() => setLoading(false))
  }

  useEffect(() => { loadTickets() }, [page, statusFilter])

  async function openTicket(ticket: Ticket) {
    setSelected(ticket)
    const msgs = await get<{ messages: Message[] }>(`/admin/tickets/${ticket.id}/messages`)
    setMessages(msgs.messages)
  }

  async function updateStatus(status: string) {
    if (!selected) return
    await put(`/admin/tickets/${selected.id}`, { status })
    setSelected(t => t ? { ...t, status } : t)
    loadTickets()
  }

  async function sendReply() {
    if (!selected || !reply.trim()) return
    setSending(true)
    await post(`/admin/tickets/${selected.id}/messages`, { message: reply })
    const msgs = await get<{ messages: Message[] }>(`/admin/tickets/${selected.id}/messages`)
    setMessages(msgs.messages)
    setReply('')
    setSending(false)
  }

  const statusCounts = {
    all: data?.total ?? 0,
    open: 0, in_progress: 0, resolved: 0
  }

  return (
    <div className="p-6 h-full flex flex-col">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-white">Support Tickets</h1>
      </div>

      {/* Status filter tabs */}
      <div className="flex gap-1 mb-4 bg-surface border border-border rounded-xl p-1 w-fit">
        {[['', 'All'], ['open', 'Open'], ['in_progress', 'In Progress'], ['resolved', 'Resolved'], ['closed', 'Closed']].map(([val, label]) => (
          <button key={val} onClick={() => { setStatusFilter(val); setPage(1) }}
            className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors ${statusFilter === val ? 'bg-primary text-white' : 'text-gray-400 hover:text-white'}`}>
            {label}
          </button>
        ))}
      </div>

      <div className="flex gap-4 flex-1 min-h-0">
        {/* Ticket list */}
        <div className="w-96 flex-shrink-0 flex flex-col min-h-0">
          <div className="bg-card border border-border rounded-2xl overflow-hidden flex-1 flex flex-col">
            <div className="overflow-y-auto flex-1">
              {loading ? (
                <div className="p-8 text-center text-gray-500">Loading...</div>
              ) : !data?.tickets?.length ? (
                <div className="p-8 text-center text-gray-500">No tickets found</div>
              ) : data.tickets.map(ticket => (
                <div key={ticket.id} onClick={() => openTicket(ticket)}
                  className={`p-4 border-b border-border/50 cursor-pointer hover:bg-white/3 transition-colors ${selected?.id === ticket.id ? 'bg-primary/10 border-l-2 border-l-primary' : ''}`}>
                  <div className="flex items-start justify-between gap-2 mb-1">
                    <span className="text-white text-sm font-medium line-clamp-1">{ticket.subject}</span>
                    <ChevronRight size={14} className="text-gray-600 flex-shrink-0 mt-0.5" />
                  </div>
                  <div className="text-xs text-gray-500 mb-2">{ticket.user_name || ticket.user_email}</div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[ticket.status] ?? ''}`}>{ticket.status.replace('_', ' ')}</span>
                    <span className={`text-xs font-medium ${PRIORITY_COLORS[ticket.priority] ?? ''}`}>● {ticket.priority}</span>
                    <span className="text-xs text-gray-600 ml-auto">{new Date(ticket.created_at).toLocaleDateString()}</span>
                  </div>
                </div>
              ))}
            </div>
            {data && data.pages > 1 && (
              <div className="p-3 border-t border-border flex justify-between">
                <button onClick={() => setPage(p => Math.max(1, p-1))} disabled={page <= 1} className="text-xs text-gray-400 disabled:opacity-30">← Prev</button>
                <span className="text-xs text-gray-500">{page}/{data.pages}</span>
                <button onClick={() => setPage(p => Math.min(data.pages, p+1))} disabled={page >= data.pages} className="text-xs text-gray-400 disabled:opacity-30">Next →</button>
              </div>
            )}
          </div>
        </div>

        {/* Ticket detail panel */}
        <div className="flex-1 min-h-0">
          {!selected ? (
            <div className="h-full bg-card border border-border rounded-2xl flex items-center justify-center">
              <div className="text-center">
                <MessageCircle size={40} className="text-gray-600 mx-auto mb-3" />
                <p className="text-gray-500">Select a ticket to view details</p>
              </div>
            </div>
          ) : (
            <div className="h-full bg-card border border-border rounded-2xl flex flex-col">
              {/* Header */}
              <div className="p-4 border-b border-border">
                <div className="flex items-start justify-between mb-2">
                  <h2 className="text-white font-semibold">{selected.subject}</h2>
                  <button onClick={() => setSelected(null)} className="text-gray-500 hover:text-white p-1"><X size={16} /></button>
                </div>
                <div className="text-xs text-gray-500 mb-3">From: {selected.user_name || selected.user_email}</div>
                <div className="flex gap-2 flex-wrap">
                  <span className={`text-xs px-2 py-1 rounded-full ${STATUS_COLORS[selected.status] ?? ''}`}>{selected.status.replace('_', ' ')}</span>
                  <span className={`text-xs font-medium ${PRIORITY_COLORS[selected.priority] ?? ''} flex items-center`}>● {selected.priority}</span>
                </div>
                {/* Status actions */}
                <div className="flex gap-2 mt-3">
                  {['open', 'in_progress', 'resolved', 'closed'].filter(s => s !== selected.status).map(s => (
                    <button key={s} onClick={() => updateStatus(s)}
                      className="px-3 py-1 text-xs bg-surface border border-border rounded-lg text-gray-300 hover:text-white hover:border-primary transition-colors capitalize">
                      → {s.replace('_', ' ')}
                    </button>
                  ))}
                </div>
              </div>

              {/* Description */}
              <div className="px-4 py-3 border-b border-border bg-surface/50">
                <p className="text-sm text-gray-300">{selected.description}</p>
              </div>

              {/* Messages */}
              <div className="flex-1 overflow-y-auto p-4 space-y-3">
                {messages.map(msg => (
                  <div key={msg.id} className={`flex ${msg.sender_role === 'admin' || msg.sender_role === 'support' ? 'justify-end' : 'justify-start'}`}>
                    <div className={`max-w-[80%] rounded-xl p-3 ${msg.sender_role === 'admin' || msg.sender_role === 'support' ? 'bg-primary/20 border border-primary/30' : 'bg-surface border border-border'}`}>
                      <div className="text-xs text-gray-500 mb-1">{msg.sender_name} ({msg.sender_role}) · {new Date(msg.created_at).toLocaleString()}</div>
                      <p className="text-sm text-white">{msg.message}</p>
                    </div>
                  </div>
                ))}
                {messages.length === 0 && (
                  <p className="text-center text-gray-500 text-sm">No messages yet</p>
                )}
              </div>

              {/* Reply box */}
              <div className="p-4 border-t border-border">
                <div className="flex gap-2">
                  <textarea value={reply} onChange={e => setReply(e.target.value)} placeholder="Type your reply..."
                    rows={2} className="flex-1 bg-surface border border-border rounded-xl px-3 py-2 text-sm text-white resize-none focus:outline-none focus:border-primary" />
                  <button onClick={sendReply} disabled={!reply.trim() || sending}
                    className="px-4 py-2 bg-primary text-white rounded-xl text-sm font-medium hover:bg-primary/90 disabled:opacity-50 transition-colors self-end">
                    {sending ? '...' : 'Send'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
