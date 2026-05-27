import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { get, put } from '../api/client'
import { ArrowLeft, CheckCircle, XCircle, Shield, Mail, Calendar, TrendingUp, BarChart2 } from 'lucide-react'

interface UserDetail {
  id: string; email: string; name: string; role: string; is_active: boolean
  created_at: string; last_login_at: string; tx_count: number; phone?: string
}
interface Transaction {
  id: string; merchant: string; amount: number; is_credit: boolean; category_slug: string
  note: string; transaction_date: string; created_at: string
}
interface BankAccount { id: string; bank_name: string; account_type: string; balance: number; color: string }
interface CreditCard { id: string; bank_name: string; card_name: string; outstanding: number; limit_amount: number }

const ROLE_COLORS: Record<string, string> = { admin: 'bg-purple-900/40 text-purple-300', support: 'bg-blue-900/40 text-blue-300', user: 'bg-green-900/40 text-green-300' }

export default function UserDetail() {
  const { id } = useParams<{ id: string }>()
  const nav = useNavigate()
  const [user, setUser] = useState<UserDetail | null>(null)
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [accounts, setAccounts] = useState<BankAccount[]>([])
  const [cards, setCards] = useState<CreditCard[]>([])
  const [tab, setTab] = useState<'txn' | 'accounts' | 'cards'>('txn')
  const [loading, setLoading] = useState(true)
  const [roleEdit, setRoleEdit] = useState(false)
  const [newRole, setNewRole] = useState('')

  useEffect(() => {
    if (!id) return
    Promise.all([
      get<{ user: UserDetail }>(`/admin/users/${id}`),
      get<{ transactions: Transaction[] }>(`/admin/users/${id}/transactions?limit=20`),
      get<{ accounts: BankAccount[] }>(`/admin/users/${id}/accounts`),
      get<{ cards: CreditCard[] }>(`/admin/users/${id}/cards`),
    ]).then(([u, t, a, c]) => {
      setUser(u.user); setTransactions(t.transactions); setAccounts(a.accounts); setCards(c.cards)
      setNewRole(u.user.role)
    }).finally(() => setLoading(false))
  }, [id])

  async function toggleStatus() {
    if (!user) return
    await put(`/admin/users/${user.id}/status`, { is_active: !user.is_active })
    setUser(u => u ? { ...u, is_active: !u.is_active } : u)
  }

  async function updateRole() {
    if (!user) return
    await put(`/admin/users/${user.id}/role`, { role: newRole })
    setUser(u => u ? { ...u, role: newRole } : u)
    setRoleEdit(false)
  }

  if (loading) return <div className="flex items-center justify-center h-full"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" /></div>
  if (!user) return <div className="p-6 text-red-400">User not found</div>

  return (
    <div className="p-6">
      <button onClick={() => nav('/users')} className="flex items-center gap-2 text-gray-400 hover:text-white mb-6 text-sm transition-colors">
        <ArrowLeft size={16} /> Back to Users
      </button>

      {/* Header */}
      <div className="bg-card border border-border rounded-2xl p-6 mb-6">
        <div className="flex items-start justify-between flex-wrap gap-4">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <div className="w-12 h-12 rounded-full bg-primary/20 flex items-center justify-center text-xl font-bold text-primary">
                {(user.name || user.email)[0].toUpperCase()}
              </div>
              <div>
                <h1 className="text-xl font-bold text-white">{user.name || '—'}</h1>
                <div className="flex items-center gap-2 text-sm text-gray-400">
                  <Mail size={12} /> {user.email}
                </div>
              </div>
            </div>
            <div className="flex items-center gap-3 flex-wrap mt-3">
              <span className={`text-xs px-2 py-1 rounded-full font-medium ${ROLE_COLORS[user.role] ?? ''}`}>{user.role}</span>
              {user.is_active
                ? <span className="flex items-center gap-1 text-xs text-green-400"><CheckCircle size={12} /> Active</span>
                : <span className="flex items-center gap-1 text-xs text-red-400"><XCircle size={12} /> Suspended</span>}
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={() => nav(`/users/${id}/analytics`)}
              className="flex items-center gap-2 px-4 py-2 bg-primary/10 text-primary border border-primary/30 rounded-xl text-sm font-medium hover:bg-primary/20 transition-colors">
              <BarChart2 size={14} /> View Analytics
            </button>
            <button onClick={toggleStatus}
              className={`px-4 py-2 rounded-xl text-sm font-medium transition-colors ${user.is_active ? 'bg-red-900/30 text-red-400 hover:bg-red-900/50' : 'bg-green-900/30 text-green-400 hover:bg-green-900/50'}`}>
              {user.is_active ? 'Suspend' : 'Activate'}
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-6">
          {[
            { label: 'User ID', value: user.id.slice(0, 8) + '...', icon: Shield },
            { label: 'Transactions', value: user.tx_count, icon: TrendingUp },
            { label: 'Joined', value: new Date(user.created_at).toLocaleDateString(), icon: Calendar },
            { label: 'Last Login', value: user.last_login_at ? new Date(user.last_login_at).toLocaleDateString() : 'Never', icon: Calendar },
          ].map(({ label, value, icon: Icon }) => (
            <div key={label} className="bg-surface rounded-xl p-3">
              <div className="flex items-center gap-2 text-xs text-gray-500 mb-1"><Icon size={12} />{label}</div>
              <div className="text-sm text-white font-medium">{value}</div>
            </div>
          ))}
        </div>

        {/* Role edit */}
        <div className="mt-4 flex items-center gap-3">
          <span className="text-xs text-gray-500">Role:</span>
          {roleEdit ? (
            <>
              <select value={newRole} onChange={e => setNewRole(e.target.value)}
                className="bg-surface border border-border rounded-lg px-3 py-1.5 text-sm text-white focus:outline-none focus:border-primary">
                <option value="user">user</option>
                <option value="support">support</option>
                <option value="admin">admin</option>
              </select>
              <button onClick={updateRole} className="px-3 py-1.5 bg-primary text-white text-xs rounded-lg">Save</button>
              <button onClick={() => { setRoleEdit(false); setNewRole(user.role) }} className="px-3 py-1.5 bg-surface border border-border text-gray-400 text-xs rounded-lg">Cancel</button>
            </>
          ) : (
            <button onClick={() => setRoleEdit(true)} className="text-xs text-primary hover:underline">Change Role</button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-4 bg-surface border border-border rounded-xl p-1 w-fit">
        {([['txn', 'Transactions'], ['accounts', 'Bank Accounts'], ['cards', 'Credit Cards']] as const).map(([key, label]) => (
          <button key={key} onClick={() => setTab(key)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${tab === key ? 'bg-primary text-white' : 'text-gray-400 hover:text-white'}`}>
            {label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div className="bg-card border border-border rounded-2xl overflow-hidden">
        {tab === 'txn' && (
          <table className="w-full text-sm">
            <thead><tr className="border-b border-border">
              {['Date', 'Merchant', 'Category', 'Amount', 'Type'].map(h => (
                <th key={h} className="text-left px-4 py-3 text-gray-400 font-medium text-xs uppercase tracking-wider">{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {transactions.length === 0
                ? <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-500">No transactions</td></tr>
                : transactions.map(tx => (
                  <tr key={tx.id} className="border-b border-border/50">
                    <td className="px-4 py-3 text-gray-500 text-xs">{new Date(tx.transaction_date).toLocaleDateString()}</td>
                    <td className="px-4 py-3 text-white">{tx.merchant}</td>
                    <td className="px-4 py-3 text-gray-400 text-xs">{tx.category_slug}</td>
                    <td className={`px-4 py-3 font-medium ${tx.is_credit ? 'text-green-400' : 'text-white'}`}>
                      {tx.is_credit ? '+' : '-'}₹{tx.amount.toLocaleString('en-IN')}
                    </td>
                    <td className="px-4 py-3"><span className={`text-xs px-2 py-0.5 rounded-full ${tx.is_credit ? 'bg-green-900/40 text-green-400' : 'bg-red-900/40 text-red-400'}`}>{tx.is_credit ? 'Credit' : 'Debit'}</span></td>
                  </tr>
                ))}
            </tbody>
          </table>
        )}

        {tab === 'accounts' && (
          <table className="w-full text-sm">
            <thead><tr className="border-b border-border">
              {['Bank', 'Type', 'Balance'].map(h => (
                <th key={h} className="text-left px-4 py-3 text-gray-400 font-medium text-xs uppercase tracking-wider">{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {accounts.length === 0
                ? <tr><td colSpan={3} className="px-4 py-8 text-center text-gray-500">No bank accounts</td></tr>
                : accounts.map(a => (
                  <tr key={a.id} className="border-b border-border/50">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <div className="w-3 h-3 rounded-full" style={{ background: a.color }} />
                        <span className="text-white">{a.bank_name}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-400">{a.account_type}</td>
                    <td className="px-4 py-3 text-green-400 font-medium">₹{a.balance.toLocaleString('en-IN')}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        )}

        {tab === 'cards' && (
          <table className="w-full text-sm">
            <thead><tr className="border-b border-border">
              {['Bank', 'Card', 'Outstanding', 'Limit', 'Utilization'].map(h => (
                <th key={h} className="text-left px-4 py-3 text-gray-400 font-medium text-xs uppercase tracking-wider">{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {cards.length === 0
                ? <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-500">No credit cards</td></tr>
                : cards.map(c => {
                  const util = c.limit_amount > 0 ? Math.round((c.outstanding / c.limit_amount) * 100) : 0
                  return (
                    <tr key={c.id} className="border-b border-border/50">
                      <td className="px-4 py-3 text-white">{c.bank_name}</td>
                      <td className="px-4 py-3 text-gray-400">{c.card_name}</td>
                      <td className="px-4 py-3 text-red-400">₹{c.outstanding.toLocaleString('en-IN')}</td>
                      <td className="px-4 py-3 text-gray-400">₹{c.limit_amount.toLocaleString('en-IN')}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 bg-surface rounded-full h-1.5">
                            <div className={`h-1.5 rounded-full ${util > 80 ? 'bg-red-500' : util > 50 ? 'bg-yellow-500' : 'bg-green-500'}`} style={{ width: `${Math.min(util, 100)}%` }} />
                          </div>
                          <span className="text-xs text-gray-400">{util}%</span>
                        </div>
                      </td>
                    </tr>
                  )
                })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
