import { useEffect, useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { get } from '../api/client'
import {
  AreaChart, Area, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts'
import { ArrowLeft, Download, Lightbulb, TrendingUp, IndianRupee, Receipt } from 'lucide-react'

interface MonthlyData { month: string; debit: number; credit: number; count: number }
interface CategoryData { category: string; count: number; volume: number }
interface AnalyticsData {
  monthly: MonthlyData[]
  by_category: CategoryData[]
  insights: string[]
  total_debit: number
  total_credit: number
  total_transactions: number
}

const COLORS = ['#6C63FF','#EC4899','#10B981','#F59E0B','#3B82F6','#8B5CF6','#EF4444','#06B6D4','#84CC16','#F97316']
const formatINR = (v: number) => v >= 10000000 ? `₹${(v/10000000).toFixed(1)}Cr` : v >= 100000 ? `₹${(v/100000).toFixed(1)}L` : v >= 1000 ? `₹${(v/1000).toFixed(0)}K` : `₹${v}`

export default function UserAnalytics() {
  const { id } = useParams<{ id: string }>()
  const nav = useNavigate()
  const [data, setData] = useState<AnalyticsData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [downloading, setDownloading] = useState(false)
  const printRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!id) return
    get<AnalyticsData>(`/admin/users/${id}/analytics`)
      .then(setData)
      .catch(e => setError(e.message || 'Failed to load analytics'))
      .finally(() => setLoading(false))
  }, [id])

  async function downloadPDF() {
    setDownloading(true)
    try {
      const { default: jsPDF } = await import('jspdf')
      const { default: html2canvas } = await import('html2canvas')
      if (!printRef.current) return
      const canvas = await html2canvas(printRef.current, { scale: 2, backgroundColor: '#0F0F1A' })
      const imgData = canvas.toDataURL('image/png')
      const pdf = new jsPDF({ orientation: 'p', unit: 'mm', format: 'a4' })
      const pageWidth = pdf.internal.pageSize.getWidth()
      const imgHeight = (canvas.height * pageWidth) / canvas.width
      let yPos = 0
      const pageHeight = pdf.internal.pageSize.getHeight()
      while (yPos < imgHeight) {
        if (yPos > 0) pdf.addPage()
        pdf.addImage(imgData, 'PNG', 0, -yPos, pageWidth, imgHeight)
        yPos += pageHeight
      }
      pdf.save(`user-analytics-${id?.slice(0, 8)}.pdf`)
    } catch (e) {
      console.error('PDF error', e)
      alert('PDF generation failed. Ensure jspdf and html2canvas are installed.')
    } finally {
      setDownloading(false)
    }
  }

  if (loading) return (
    <div className="flex items-center justify-center h-full">
      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
    </div>
  )
  if (error) return <div className="p-6 text-red-400">{error}</div>
  if (!data) return null

  const kpis = [
    { label: 'Total Spent', value: formatINR(Number(data.total_debit)), icon: IndianRupee, color: 'text-red-400', bg: 'bg-red-900/20' },
    { label: 'Total Credited', value: formatINR(Number(data.total_credit)), icon: TrendingUp, color: 'text-green-400', bg: 'bg-green-900/20' },
    { label: 'Transactions', value: data.total_transactions.toLocaleString(), icon: Receipt, color: 'text-primary', bg: 'bg-primary/10' },
    { label: 'Top Category', value: data.by_category[0]?.category || '—', icon: Lightbulb, color: 'text-yellow-400', bg: 'bg-yellow-900/20' },
  ]

  const monthlyChart = data.monthly.map(m => ({ ...m, debit: Number(m.debit), credit: Number(m.credit) }))

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <button onClick={() => nav(`/users/${id}`)} className="flex items-center gap-2 text-gray-400 hover:text-white text-sm transition-colors">
          <ArrowLeft size={16} /> Back to User
        </button>
        <button onClick={downloadPDF} disabled={downloading}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-white rounded-xl text-sm font-medium hover:bg-primary/90 transition-colors disabled:opacity-60">
          <Download size={14} /> {downloading ? 'Generating PDF...' : 'Download PDF'}
        </button>
      </div>

      <div ref={printRef}>
        <h1 className="text-2xl font-bold text-white mb-6">User Spending Analytics</h1>

        {/* KPIs */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          {kpis.map(({ label, value, icon: Icon, color, bg }) => (
            <div key={label} className="bg-card border border-border rounded-2xl p-5">
              <div className="flex items-center justify-between mb-3">
                <span className="text-xs text-gray-500 uppercase tracking-wider">{label}</span>
                <div className={`p-2 rounded-lg ${bg}`}><Icon size={14} className={color} /></div>
              </div>
              <div className={`text-2xl font-black ${color} capitalize`}>{value}</div>
            </div>
          ))}
        </div>

        {/* Monthly Debit vs Credit */}
        <div className="bg-card border border-border rounded-2xl p-5 mb-4">
          <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">Monthly Spending vs Income (6 months)</h2>
          {monthlyChart.length === 0
            ? <p className="text-gray-500 text-sm py-8 text-center">No data for the last 6 months</p>
            : <ResponsiveContainer width="100%" height={220}>
                <AreaChart data={monthlyChart}>
                  <defs>
                    <linearGradient id="debitG" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#EF4444" stopOpacity={0.3}/><stop offset="95%" stopColor="#EF4444" stopOpacity={0}/>
                    </linearGradient>
                    <linearGradient id="creditG" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#10B981" stopOpacity={0.3}/><stop offset="95%" stopColor="#10B981" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1E2036" />
                  <XAxis dataKey="month" tick={{ fill: '#64748B', fontSize: 11 }} />
                  <YAxis tick={{ fill: '#64748B', fontSize: 11 }} tickFormatter={formatINR} />
                  <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }}
                    formatter={(v: number, name: string) => [formatINR(v), name === 'debit' ? 'Spent' : 'Received']} />
                  <Legend formatter={name => name === 'debit' ? 'Spent' : 'Received'} />
                  <Area type="monotone" dataKey="debit" stroke="#EF4444" fill="url(#debitG)" strokeWidth={2} />
                  <Area type="monotone" dataKey="credit" stroke="#10B981" fill="url(#creditG)" strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>}
        </div>

        {/* Category Breakdown */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
          <div className="bg-card border border-border rounded-2xl p-5">
            <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">Spending by Category (3 months)</h2>
            {data.by_category.length === 0
              ? <p className="text-gray-500 text-sm py-8 text-center">No data available</p>
              : <ResponsiveContainer width="100%" height={200}>
                  <PieChart>
                    <Pie data={data.by_category} cx="50%" cy="50%" outerRadius={80} dataKey="volume" nameKey="category">
                      {data.by_category.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                    </Pie>
                    <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }}
                      formatter={(v: number, name: string) => [formatINR(v), name]} />
                  </PieChart>
                </ResponsiveContainer>}
          </div>

          <div className="bg-card border border-border rounded-2xl p-5">
            <h2 className="text-sm font-semibold text-gray-400 mb-4 uppercase tracking-wider">Top Categories by Volume</h2>
            {data.by_category.length === 0
              ? <p className="text-gray-500 text-sm py-8 text-center">No data available</p>
              : <ResponsiveContainer width="100%" height={200}>
                  <BarChart data={data.by_category} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" stroke="#1E2036" />
                    <XAxis type="number" tick={{ fill: '#64748B', fontSize: 10 }} tickFormatter={formatINR} />
                    <YAxis type="category" dataKey="category" tick={{ fill: '#64748B', fontSize: 11 }} width={80} />
                    <Tooltip contentStyle={{ background: '#1A1A28', border: '1px solid #1E2036', borderRadius: '8px' }}
                      formatter={(v: number) => [formatINR(v), 'Volume']} />
                    <Bar dataKey="volume" radius={[0,4,4,0]}>
                      {data.by_category.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>}
          </div>
        </div>

        {/* Category detail table */}
        <div className="bg-card border border-border rounded-2xl overflow-hidden mb-4">
          <div className="px-5 py-4 border-b border-border">
            <h2 className="text-sm font-semibold text-gray-400 uppercase tracking-wider">Category Breakdown Detail</h2>
          </div>
          <table className="w-full text-sm">
            <thead><tr className="border-b border-border">
              {['Category', 'Transactions', 'Total Spent', 'Avg per Txn'].map(h => (
                <th key={h} className="text-left px-4 py-3 text-gray-400 font-medium text-xs uppercase tracking-wider">{h}</th>
              ))}
            </tr></thead>
            <tbody>
              {data.by_category.length === 0
                ? <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-500">No data</td></tr>
                : data.by_category.map((cat, i) => (
                    <tr key={cat.category} className="border-b border-border/50 hover:bg-white/2">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-2 h-2 rounded-full" style={{ background: COLORS[i % COLORS.length] }} />
                          <span className="text-white capitalize">{cat.category}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-gray-300">{cat.count}</td>
                      <td className="px-4 py-3 text-gray-300">{formatINR(Number(cat.volume))}</td>
                      <td className="px-4 py-3 text-gray-300">₹{cat.count > 0 ? Math.round(Number(cat.volume)/cat.count).toLocaleString('en-IN') : 0}</td>
                    </tr>
                  ))}
            </tbody>
          </table>
        </div>

        {/* AI Insights */}
        {data.insights.length > 0 && (
          <div className="bg-card border border-primary/30 rounded-2xl p-5">
            <div className="flex items-center gap-2 mb-4">
              <div className="p-2 bg-primary/10 rounded-lg"><Lightbulb size={16} className="text-primary" /></div>
              <h2 className="text-sm font-semibold text-white uppercase tracking-wider">AI-Powered Spending Insights</h2>
            </div>
            <div className="space-y-3">
              {data.insights.map((insight, i) => (
                <div key={i} className="flex items-start gap-3 bg-surface rounded-xl p-3">
                  <div className="w-1.5 h-1.5 rounded-full bg-primary mt-2 flex-shrink-0" />
                  <p className="text-sm text-gray-300" dangerouslySetInnerHTML={{ __html: insight.replace(/\*\*(.+?)\*\*/g, '<strong class="text-white">$1</strong>') }} />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
