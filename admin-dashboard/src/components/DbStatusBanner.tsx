import { useEffect, useState } from 'react'
import { CheckCircle, XCircle, AlertCircle, Database, Wifi } from 'lucide-react'
import axios from 'axios'

interface ServiceStatus {
  name: string
  url: string
  status: 'ok' | 'error' | 'checking'
}

const SERVICES: { name: string; port: number; path: string }[] = [
  { name: 'Gateway', port: 3000, path: '/health' },
  { name: 'Auth', port: 3001, path: '/health' },
  { name: 'Transactions', port: 3002, path: '/health' },
  { name: 'Users', port: 3003, path: '/health' },
  { name: 'Analytics', port: 3004, path: '/health' },
]

export default function DbStatusBanner() {
  const [services, setServices] = useState<ServiceStatus[]>(
    SERVICES.map(s => ({ name: s.name, url: `http://localhost:${s.port}`, status: 'checking' }))
  )
  const [dbMode, setDbMode] = useState<'local' | 'cloud' | 'unknown'>('unknown')
  const [expanded, setExpanded] = useState(false)

  async function checkServices() {
    const results = await Promise.all(
      SERVICES.map(async s => {
        try {
          const r = await axios.get(`http://localhost:${s.port}${s.path}`, { timeout: 2000 })
          // Detect cloud vs local DB from gateway response
          if (s.name === 'Gateway' && r.data) {
            const dbUrl = r.data?.db_url || ''
            setDbMode(dbUrl.includes('supabase') || dbUrl.includes('neon') || dbUrl.includes('render') ? 'cloud' : 'local')
          }
          return { name: s.name, url: `http://localhost:${s.port}`, status: 'ok' as const }
        } catch {
          return { name: s.name, url: `http://localhost:${s.port}`, status: 'error' as const }
        }
      })
    )
    setServices(results)
  }

  useEffect(() => {
    checkServices()
    const interval = setInterval(checkServices, 30000)
    return () => clearInterval(interval)
  }, [])

  const allOk = services.every(s => s.status === 'ok')
  const hasError = services.some(s => s.status === 'error')
  const checking = services.some(s => s.status === 'checking')

  const statusColor = checking ? 'border-yellow-500/30 bg-yellow-950/20'
    : allOk ? 'border-green-500/30 bg-green-950/20'
    : 'border-red-500/30 bg-red-950/20'

  const StatusIcon = checking ? AlertCircle : allOk ? CheckCircle : XCircle
  const statusIconColor = checking ? 'text-yellow-400' : allOk ? 'text-green-400' : 'text-red-400'

  return (
    <div className={`border rounded-xl p-3 mx-3 mb-3 cursor-pointer transition-all ${statusColor}`}
      onClick={() => setExpanded(e => !e)}>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <StatusIcon size={14} className={statusIconColor} />
          <span className="text-xs font-medium text-gray-300">
            {checking ? 'Checking services...' : allOk ? 'All services online' : `${services.filter(s=>s.status==='error').length} service(s) down`}
          </span>
        </div>
        <div className="flex items-center gap-2">
          {dbMode !== 'unknown' && (
            <span className={`flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium
              ${dbMode === 'cloud' ? 'bg-blue-900/40 text-blue-300' : 'bg-gray-800 text-gray-400'}`}>
              {dbMode === 'cloud' ? <><Wifi size={10} /> Cloud DB</> : <><Database size={10} /> Local DB</>}
            </span>
          )}
          <span className="text-gray-600 text-xs">{expanded ? '▲' : '▼'}</span>
        </div>
      </div>

      {expanded && (
        <div className="mt-3 space-y-1.5">
          {services.map(s => (
            <div key={s.name} className="flex items-center justify-between">
              <span className="text-xs text-gray-400">{s.name}</span>
              <div className="flex items-center gap-1.5">
                <span className="text-xs text-gray-600">{s.url}</span>
                {s.status === 'ok'
                  ? <CheckCircle size={12} className="text-green-400" />
                  : s.status === 'error'
                  ? <XCircle size={12} className="text-red-400" />
                  : <div className="w-3 h-3 rounded-full border border-yellow-400 border-t-transparent animate-spin" />}
              </div>
            </div>
          ))}
          <button onClick={e => { e.stopPropagation(); checkServices() }}
            className="mt-2 text-xs text-primary hover:underline">
            Refresh status
          </button>
        </div>
      )}
    </div>
  )
}
