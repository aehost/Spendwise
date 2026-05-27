import { useEffect, useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { get, put, post } from '../api/client'
import { ArrowLeft, Send, Clock, User, AlertCircle, CheckCircle, RefreshCw } from 'lucide-react'

interface Ticket {
  id: string; subject: string; description: string; status: string; priority: string
  user_id: string; user_email: string; user_name: string
  assigned_to_email?: string; created_at: string; updated_at: string
}
interface Message {
  id: string; sender_id: string; sender_role: string; sender_name: string
  message: string; created_at: string
}

const STATUS_OPTIONS = ['open', 'in_progress', 'resolved', 'closed']
const PRIORITY_OPTIONS = ['low', 'medium', 'high', 'urgent']

const STATUS_COLORS: Record<string, string> = {
  open: 'bg-yellow-900/40 text-yellow-400',
  in_progress: 'bg-blue-900/40 text-blue-400',
  resolved: 'bg-green-900/40 text-green-400',
  closed: 'bg-gray-700 text-gray-400',
}
const PRIORITY_COLORS: Record<string, string> = {
  low: 'text-gray-400', medium: 'text-yellow-400', high: 'text-orange-400', urgent: 'text-red-500'
}

export default function TicketDetail() {
  const { id } = useParams<{ id: string }>()
  const nav = useNavigate()
  const [ticket, setTicket] = useState<Ticket | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [loading, setLoading] = useState(true)
  const [reply, setReply] = useState('')
  const [sending, setSending] = useState(false)
  const [updating, setUpdating] = useState(false)
  const messagesEnd = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!id) return
    Promise.all([
      get<{ ticket: Ticket }>(`/support/tickets/${id}`),
      get<{ messages: Message[] }>(`/support/tickets/${id}/messages`),
    ]).then(([t, m]) => {
      setTicket(t.ticket); setMessages(m.messages)
    }).finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    messagesEnd.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  async function updateTicket(field: string, value: string) {
    if (!ticket) return
    setUpdating(true)
    await put(`/support/tickets/${ticket.id}`, { [field]: value })
    setTicket(t => t ? { ...t, [field]: value } : t)
    setUpdating(false)
  }

  async function sendReply() {
    if (!ticket || !reply.trim()) return
    setSending(true)
    await post(`/support/tickets/${ticket.id}/messages`, { message: reply.trim() })
    const msgs = await get<{ messages: Message[] }>(`/support/tickets/${ticket.id}/messages`)
    setMessages(msgs.messages)
    setReply('')
    // Auto-move to in_progress if still open
    if (ticket.status === 'open') { await updateTicket('status', 'in_progress') }
    setSending(false)
  }

  if (loading) return <div className="flex items-center justify-center h-full"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" /></div>
  if (!ticket) return <div className="p-6 text-red-400">Ticket not found</div>

  const supportUser = JSON.parse(localStorage.getItem('sw_support_user') || '{}')

  return (
    <div className="flex flex-col h-full">
      {/* Top bar */}
      <div className="flex-shrink-0 p-4 border-b border-border bg-surface">
        <div className="flex items-center gap-3 mb-3">
          <button onClick={() => nav('/')} className="flex items-center gap-1.5 text-gray-400 hover:text-white text-sm transition-colors">
            <ArrowLeft size={15} /> Back
          </button>
          <div className="text-gray-600">/</div>
          <span className="text-gray-400 text-sm">{ticket.subject}</span>
        </div>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <h1 className="text-lg font-bold text-white mb-1">{ticket.subject}</h1>
            <div className="flex items-center gap-2 text-xs text-gray-500">
              <User size={12} />
              <span>{ticket.user_name || ticket.user_email}</span>
              <Clock size={12} className="ml-2" />
              <span>{new Date(ticket.created_at).toLocaleString()}</span>
            </div>
          </div>
          <div className="flex items-center gap-3 flex-wrap">
            {/* Status selector */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-gray-500">Status:</span>
              <select value={ticket.status} onChange={e => updateTicket('status', e.target.value)} disabled={updating}
                className={`text-xs px-2 py-1 rounded-full font-medium border-none focus:outline-none cursor-pointer ${STATUS_COLORS[ticket.status] ?? ''} bg-transparent`}>
                {STATUS_OPTIONS.map(s => <option key={s} value={s} className="bg-card text-white">{s.replace('_', ' ')}</option>)}
              </select>
            </div>
            {/* Priority selector */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-gray-500">Priority:</span>
              <select value={ticket.priority} onChange={e => updateTicket('priority', e.target.value)} disabled={updating}
                className={`text-xs font-medium bg-transparent border-none focus:outline-none cursor-pointer ${PRIORITY_COLORS[ticket.priority] ?? ''}`}>
                {PRIORITY_OPTIONS.map(p => <option key={p} value={p} className="bg-card text-white">{p}</option>)}
              </select>
            </div>
            {updating && <RefreshCw size={14} className="text-gray-500 animate-spin" />}
          </div>
        </div>
      </div>

      {/* Description banner */}
      <div className="flex-shrink-0 px-4 py-3 bg-card/50 border-b border-border">
        <p className="text-sm text-gray-300 leading-relaxed">{ticket.description}</p>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.length === 0 ? (
          <div className="text-center text-gray-500 py-8">
            <AlertCircle size={32} className="mx-auto mb-3 text-gray-600" />
            <p>No messages yet. Send the first reply.</p>
          </div>
        ) : messages.map(msg => {
          const isSupport = msg.sender_role === 'support' || msg.sender_role === 'admin'
          const isCurrentUser = msg.sender_id === supportUser.id
          return (
            <div key={msg.id} className={`flex ${isSupport ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[75%] ${isSupport ? 'items-end' : 'items-start'} flex flex-col gap-1`}>
                <div className={`text-xs ${isSupport ? 'text-right' : ''} text-gray-500`}>
                  {msg.sender_name}
                  {isCurrentUser && ' (you)'}
                  {' · '}
                  {new Date(msg.created_at).toLocaleString()}
                </div>
                <div className={`rounded-2xl px-4 py-3 text-sm leading-relaxed ${
                  isSupport
                    ? 'bg-primary/20 border border-primary/30 text-white rounded-tr-sm'
                    : 'bg-surface border border-border text-gray-200 rounded-tl-sm'
                }`}>
                  {msg.message}
                </div>
              </div>
            </div>
          )
        })}
        <div ref={messagesEnd} />
      </div>

      {/* Quick actions */}
      <div className="flex-shrink-0 px-4 py-2 border-t border-border/50 bg-surface/50">
        <div className="flex gap-2 flex-wrap">
          {[
            'Thank you for contacting SpendWise support.',
            'Could you please provide more details?',
            'I\'ve escalated this to our technical team.',
            'This has been resolved. Please let us know if you need anything else.',
          ].map(template => (
            <button key={template} onClick={() => setReply(template)}
              className="text-xs px-3 py-1.5 bg-card border border-border rounded-full text-gray-400 hover:text-white hover:border-primary transition-colors">
              {template.slice(0, 30)}...
            </button>
          ))}
        </div>
      </div>

      {/* Reply box */}
      <div className="flex-shrink-0 p-4 border-t border-border bg-surface">
        {ticket.status === 'resolved' || ticket.status === 'closed' ? (
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-sm text-green-400">
              <CheckCircle size={16} />
              <span>Ticket is {ticket.status}</span>
            </div>
            <button onClick={() => updateTicket('status', 'open')}
              className="px-4 py-2 text-sm border border-border rounded-xl text-gray-300 hover:text-white hover:border-primary transition-colors">
              Reopen Ticket
            </button>
          </div>
        ) : (
          <div className="flex gap-3 items-end">
            <textarea
              value={reply}
              onChange={e => setReply(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) sendReply() }}
              placeholder="Type your reply... (Ctrl+Enter to send)"
              rows={3}
              className="flex-1 bg-card border border-border rounded-xl px-4 py-3 text-sm text-white resize-none focus:outline-none focus:border-primary"
            />
            <button onClick={sendReply} disabled={!reply.trim() || sending}
              className="flex items-center gap-2 px-5 py-3 bg-primary hover:bg-primary/90 text-white rounded-xl text-sm font-medium disabled:opacity-50 transition-colors flex-shrink-0">
              <Send size={15} />
              {sending ? 'Sending...' : 'Send'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
