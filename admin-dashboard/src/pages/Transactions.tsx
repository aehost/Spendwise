import { useEffect, useState, useRef } from 'react'
import { get } from '../api/client'
import { Search, Download, Filter } from 'lucide-react'

interface Transaction {
  id: string; user_email: string; user_name: string; merchant: string; amount: number
  is_credit: boolean; category_slug: string; transaction_date: string; bank_name?: string
}
interface TxResponse { transactions: Transaction[]; total: number; page: number; pages: number }

const CATEGORIES = ['food','shopping','bills','travel','health','entertainment','fuel','education','salary','investment','emi','transfer','business','other']

export default function Transactions() {
  const [data, setData] = useState<TxResponse | null>(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const [category, setCategory] = useState('')
  const [type, setType] = useState('')
  const [loading, setLoading] = useState(true)
  const [showFilters, setShowFilters] = useState(false)
  const searchTimer = useRef<ReturnType<typeof setTimeout>>()

  function load(p = page, s = search, cat = category, t = type) {
    setLoading(true)
    const params: Record<string, any> = { page: p, limit: 25 }
    if (s) params.search = s
    if (cat) params.category = cat
    if (t === 'credit') params.is_credit = true
    if (t === 'debit') params.is_credit = false
    get<TxResponse>('/admin/transactions', params)
      .then(d => { setData(d); setLoading(false) })
      .catch(() => setLoading(false))
  }

  useEffect(() => { load() }, [page])

  useEffect(() => {
    clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(() => { setPage(1); load(1, search, category, type) }, 400)
    return () => clearTimeout(searchTimer.current)
  }, [search, category, type])

  function exportCSV() {
    if (!data?.transactions) return
    const rows = [['Date', 'User', 'Merchant', 'Amount', 'Type', 'Category', 'Bank']]
    data.transactions.forEach(tx => rows.push([
      new Date(tx.transaction_date).toLocaleDateString(),
      tx.user_email, tx.merchant,
      tx.amount.toString(),
      tx.is_credit ? 'Credit' : 'Debit',
      tx.category_slug, tx.bank_name || ''
    ]))
    const csv = rows.map(r => r.map(c => `"${c}"`).join(',')).join('\n')
    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = 'transactions.csv'; a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Transactions</h1>
          <p className="text-gray-500 text-sm mt-1">{data?.total ?? 0} total records</p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => setShowFilters(!showFilters)} className="flex items-center gap-2 px-4 py-2 bg-card border border-border rounded-xl text-sm text-gray-300 hover:text-white transition-colors">
            <Filter size={14} /> Filters
          </button>
          <button onClick={exportCSV} className="flex items-center gap-2 px-4 py-2 bg-primary text-white rounded-xl text-sm font-medium hover:bg-primary/90 transition-colors">
            <Download size={14} /> Export CSV
          </button>
        </div>
      </div>

      {/* Search + filters */}
      <div className="mb-4 space-y-3">
        <div className="relative">
          <Search size={16} className="absolute left-3 top-3 text-gray-500" />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search merchant, user email..."
            className="w-full bg-card border border-border rounded-xl pl-9 pr-4 py-2.5 text-sm text-white focus:outline-none focus:border-primary" />
        </div>
        {showFilters && (
          <div className="flex gap-3 flex-wrap">
            <select value={category} onChange={e => { setCategory(e.target.value); setPage(1) }}
              className="bg-card border border-border rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-primary">
              <option value="">All Categories</option>
              {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
            <select value={type} onChange={e => { setType(e.target.value); setPage(1) }}
              className="bg-card border border-border rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-primary">
              <option value="">All Types</option>
              <option value="credit">Credit</option>
              <option value="debit">Debit</option>
            </select>
            <button onClick={() => { setCategory(''); setType(''); setSearch(''); setPage(1) }}
              className="px-3 py-2 text-xs text-gray-400 hover:text-white border border-border rounded-xl bg-card transition-colors">
              Clear Filters
            </button>
          </div>
        )}
      </div>

      <div className="bg-card border border-border rounded-2xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border">
              {['Date', 'User', 'Merchant', 'Category', 'Amount', 'Type', 'Bank'].map(h => (
                <th key={h} className="text-left px-4 py-3 text-gray-400 font-medium text-xs uppercase tracking-wider">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-500">Loading...</td></tr>
            ) : !data?.transactions?.length ? (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-500">No transactions found</td></tr>
            ) : data.transactions.map(tx => (
              <tr key={tx.id} className="border-b border-border/50 hover:bg-white/2">
                <td className="px-4 py-3 text-gray-500 text-xs whitespace-nowrap">{new Date(tx.transaction_date).toLocaleDateString()}</td>
                <td className="px-4 py-3">
                  <div className="text-white text-xs">{tx.user_name || '—'}</div>
                  <div className="text-gray-500 text-xs">{tx.user_email}</div>
                </td>
                <td className="px-4 py-3 text-white">{tx.merchant}</td>
                <td className="px-4 py-3 text-gray-400 text-xs">{tx.category_slug}</td>
                <td className={`px-4 py-3 font-medium ${tx.is_credit ? 'text-green-400' : 'text-white'}`}>
                  {tx.is_credit ? '+' : '-'}₹{tx.amount.toLocaleString('en-IN')}
                </td>
                <td className="px-4 py-3">
                  <span className={`text-xs px-2 py-0.5 rounded-full ${tx.is_credit ? 'bg-green-900/40 text-green-400' : 'bg-red-900/40 text-red-400'}`}>
                    {tx.is_credit ? 'Credit' : 'Debit'}
                  </span>
                </td>
                <td className="px-4 py-3 text-gray-500 text-xs">{tx.bank_name || '—'}</td>
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
