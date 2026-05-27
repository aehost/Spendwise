import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { post } from '../api/client'
import { Headphones } from 'lucide-react'

export default function Login() {
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true); setError('')
    try {
      const data = await post<{ accessToken: string; refreshToken: string; user: { id: string; email: string; name: string; role: string } }>('/auth/login', { email, password })
      if (!['admin', 'support'].includes(data.user.role)) {
        setError('Access denied. Support or admin role required.')
        setLoading(false); return
      }
      localStorage.setItem('sw_support_token', data.accessToken)
      localStorage.setItem('sw_support_user', JSON.stringify(data.user))
      nav('/')
    } catch (e: any) {
      setError(e.response?.data?.error || 'Login failed')
    } finally { setLoading(false) }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary/20 mb-4">
            <Headphones size={28} className="text-primary" />
          </div>
          <div className="text-2xl font-black text-white">SpendWise Support</div>
          <div className="text-sm text-gray-500 mt-1">Customer Support Portal</div>
        </div>
        <form onSubmit={handleSubmit} className="bg-card rounded-2xl p-6 border border-border">
          <div className="mb-4">
            <label className="block text-xs text-gray-400 mb-1.5 uppercase tracking-wider">Email</label>
            <input value={email} onChange={e => setEmail(e.target.value)} type="email" required
              className="w-full bg-surface border border-border rounded-xl px-4 py-3 text-white text-sm focus:outline-none focus:border-primary" />
          </div>
          <div className="mb-6">
            <label className="block text-xs text-gray-400 mb-1.5 uppercase tracking-wider">Password</label>
            <input value={password} onChange={e => setPassword(e.target.value)} type="password" required
              className="w-full bg-surface border border-border rounded-xl px-4 py-3 text-white text-sm focus:outline-none focus:border-primary" />
          </div>
          {error && <div className="mb-4 text-sm text-red-400 bg-red-900/20 rounded-lg p-3">{error}</div>}
          <button type="submit" disabled={loading}
            className="w-full bg-primary hover:bg-primary/90 text-white font-bold py-3 rounded-xl transition-colors disabled:opacity-50">
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
          <p className="text-xs text-gray-600 text-center mt-4">Use your SpendWise support credentials</p>
        </form>
      </div>
    </div>
  )
}
