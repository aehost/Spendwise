import { useState, useRef } from 'react'
import { get } from '../api/client'
import { Search, User, CreditCard, Building2, TrendingDown, Mail, Calendar } from 'lucide-react'

interface UserResult {
  id: string; email: string; name: string; role: string; is_active: boolean
  created_at: string; last_login_at: string; tx_count: number; phone?: string
}
interface RecentTx { merchant: string; amount: number; is_credit: boolean; transaction_date: string }

export default function UserLookup() {
  const [query, setQuery] = useState('')
  const [user, setUser] = useState<UserResult | null>(null)
  const [transactions, setTransactions] = useState<RecentTx[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const searchTimer = useRef<ReturnType<typeof setTimeout>>()

  async function search(q: string) {
    if (!q.trim() || q.length < 3) { setUser(null); setTransactions([]); return }
    setLoading(true); setError('')
    try {
      const result = await get<{ users: UserResult[] }>('/support/users', { search: q, limit: 1 })
      if (result.users.length > 0) {
        const u = result.users[0]
        setUser(u)
        const txData = await get<{ transactions: RecentTx[] }>(`/support/users/${u.id}/transactions?limit=10`)
        setTransactions(txData.transactions)
      } else {
        setUser(null); setTransactions([])
        setError('No user found matching that email or name')
      }
    } catch {
      setError('Search failed. Please try again.')
    } finally { setLoading(false) }
  }

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const q = e.target.value
    setQuery(q)
    clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(() => search(q), 500)
  }

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold text-white mb-2">User Lookup</h1>
      <p className="text-gray-500 text-sm mb-6">Search for a user to view their account details and transaction history.</p>

      {/* Search */}
      <div className="relative mb-6 max-w-lg">
        <Search size={16} className="absolute left-4 top-3.5 text-gray-500" />
        <input
          value={query}
          onChange={handleChange}
          placeholder="Search by email or name (min. 3 characters)..."
          className="w-full bg-card border border-border rounded-2xl pl-11 pr-4 py-3 text-sm text-white focus:outline-none focus:border-primary"
        />
        {loading && (
          <div className="absolute right-4 top-3.5 animate-spin rounded-full h-4 w-4 border-b-2 border-primary" />
        )}
      </div>

      {error && (
        <div className="bg-yellow-900/20 border border-yellow-800/50 rounded-xl p-4 mb-4 text-yellow-400 text-sm">
          {error}
        </div>
      )}

      {user && (
        <div className="space-y-4">
          {/* User card */}
          <div className="bg-card border border-border rounded-2xl p-6">
            <div className="flex items-start gap-4 mb-5">
              <div className="w-12 h-12 rounded-full bg-primary/20 flex items-center justify-center text-lg font-bold text-primary flex-shrink-0">
                {(user.name || user.email)[0].toUpperCase()}
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <h2 className="text-white font-semibold">{user.name || '(No name)'}</h2>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${user.is_active ? 'bg-green-900/40 text-green-400' : 'bg-red-900/40 text-red-400'}`}>
                    {user.is_active ? 'Active' : 'Suspended'}
                  </span>
                  <span className="text-xs px-2 py-0.5 rounded-full bg-purple-900/40 text-purple-300 capitalize">{user.role}</span>
                </div>
                <div className="flex items-center gap-1.5 text-sm text-gray-400">
                  <Mail size={12} /> {user.email}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {[
                { icon: TrendingDown, label: 'Total Transactions', value: user.tx_count },
                { icon: Calendar, label: 'Joined', value: new Date(user.created_at).toLocaleDateString() },
                { icon: Calendar, label: 'Last Login', value: user.last_login_at ? new Date(user.last_login_at).toLocaleDateString() : 'Never' },
                { icon: User, label: 'User ID', value: user.id.slice(0, 12) + '...' },
              ].map(({ icon: Icon, label, value }) => (
                <div key={label} className="bg-surface rounded-xl p-3">
                  <div className="flex items-center gap-1.5 text-xs text-gray-500 mb-1"><Icon size={11} />{label}</div>
                  <div className="text-sm text-white">{value}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Recent transactions */}
          <div className="bg-card border border-border rounded-2xl overflow-hidden">
            <div className="px-5 py-4 border-b border-border flex items-center gap-2">
              <TrendingDown size={14} className="text-gray-400" />
              <h3 className="text-sm font-semibold text-white">Recent Transactions</h3>
              <span className="text-xs text-gray-500 ml-auto">(last 10)</span>
            </div>
            {transactions.length === 0 ? (
              <div className="p-8 text-center text-gray-500">No transactions</div>
            ) : (
              <table className="w-full text-sm">
                <thead><tr className="border-b border-border">
                  {['Date', 'Merchant', 'Amount', 'Type'].map(h => (
                    <th key={h} className="text-left px-4 py-3 text-gray-400 font-medium text-xs uppercase tracking-wider">{h}</th>
                  ))}
                </tr></thead>
                <tbody>
                  {transactions.map((tx, i) => (
                    <tr key={i} className="border-b border-border/50">
                      <td className="px-4 py-3 text-gray-500 text-xs">{new Date(tx.transaction_date).toLocaleDateString()}</td>
                      <td className="px-4 py-3 text-white">{tx.merchant}</td>
                      <td className={`px-4 py-3 font-medium ${tx.is_credit ? 'text-green-400' : 'text-white'}`}>
                        {tx.is_credit ? '+' : '-'}₹{tx.amount.toLocaleString('en-IN')}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`text-xs px-2 py-0.5 rounded-full ${tx.is_credit ? 'bg-green-900/40 text-green-400' : 'bg-red-900/40 text-red-400'}`}>
                          {tx.is_credit ? 'Credit' : 'Debit'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* Privacy notice */}
          <div className="bg-yellow-900/10 border border-yellow-800/30 rounded-xl px-4 py-3 text-xs text-yellow-600">
            ⚠️ This information is confidential. Access is logged for compliance. Only access what is necessary to resolve the customer's issue.
          </div>
        </div>
      )}

      {!user && !loading && !error && query.length === 0 && (
        <div className="text-center py-16">
          <Search size={48} className="text-gray-700 mx-auto mb-4" />
          <p className="text-gray-500">Enter a customer's email or name to look them up</p>
        </div>
      )}
    </div>
  )
}
