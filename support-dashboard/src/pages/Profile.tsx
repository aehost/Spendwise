import { useState } from 'react'
import { post } from '../api/client'
import { User, Key, CheckCircle, Shield } from 'lucide-react'

export default function Profile() {
  const user = JSON.parse(localStorage.getItem('sw_support_user') || '{}')
  const [pwForm, setPwForm] = useState({ current: '', newPw: '', confirm: '' })
  const [pwLoading, setPwLoading] = useState(false)
  const [pwMsg, setPwMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  async function changePassword(e: React.FormEvent) {
    e.preventDefault()
    if (pwForm.newPw !== pwForm.confirm) { setPwMsg({ type: 'error', text: 'Passwords do not match' }); return }
    if (pwForm.newPw.length < 8) { setPwMsg({ type: 'error', text: 'Password must be at least 8 characters' }); return }
    setPwLoading(true); setPwMsg(null)
    try {
      await post('/auth/change-password', { currentPassword: pwForm.current, newPassword: pwForm.newPw })
      setPwMsg({ type: 'success', text: 'Password changed successfully' })
      setPwForm({ current: '', newPw: '', confirm: '' })
    } catch (err: any) {
      setPwMsg({ type: 'error', text: err.response?.data?.error || 'Failed to change password' })
    } finally { setPwLoading(false) }
  }

  return (
    <div className="p-6 max-w-xl">
      <h1 className="text-2xl font-bold text-white mb-6">My Profile</h1>

      {/* Profile info */}
      <div className="bg-card border border-border rounded-2xl p-6 mb-6">
        <div className="flex items-center gap-3 mb-5">
          <User size={16} className="text-primary" />
          <h2 className="text-base font-semibold text-white">Account Information</h2>
        </div>

        <div className="flex items-center gap-4 mb-6">
          <div className="w-16 h-16 rounded-full bg-primary/20 flex items-center justify-center text-2xl font-bold text-primary">
            {(user.name || user.email || 'S')[0].toUpperCase()}
          </div>
          <div>
            <div className="text-white font-semibold text-lg">{user.name || '(No name)'}</div>
            <div className="text-gray-400 text-sm">{user.email}</div>
            <div className="flex items-center gap-1.5 mt-1">
              <Shield size={12} className="text-primary" />
              <span className="text-xs text-primary capitalize">{user.role} Role</span>
            </div>
          </div>
        </div>

        <div className="bg-surface rounded-xl p-4">
          <div className="text-xs text-gray-500 mb-3 uppercase tracking-wider font-medium">Access Permissions</div>
          <ul className="space-y-2 text-sm text-gray-300">
            <li className="flex items-center gap-2"><CheckCircle size={13} className="text-green-400" /> View and manage support tickets</li>
            <li className="flex items-center gap-2"><CheckCircle size={13} className="text-green-400" /> Look up customer accounts</li>
            <li className="flex items-center gap-2"><CheckCircle size={13} className="text-green-400" /> View customer transaction history</li>
            <li className="flex items-center gap-2"><CheckCircle size={13} className="text-yellow-500" /> Read-only access to user data</li>
          </ul>
        </div>
      </div>

      {/* Change password */}
      <div className="bg-card border border-border rounded-2xl p-6">
        <div className="flex items-center gap-3 mb-4">
          <Key size={16} className="text-yellow-400" />
          <h2 className="text-base font-semibold text-white">Change Password</h2>
        </div>
        <form onSubmit={changePassword} className="space-y-4">
          {[
            { label: 'Current Password', field: 'current' as const, value: pwForm.current },
            { label: 'New Password', field: 'newPw' as const, value: pwForm.newPw },
            { label: 'Confirm New Password', field: 'confirm' as const, value: pwForm.confirm },
          ].map(({ label, field, value }) => (
            <div key={field}>
              <label className="block text-xs text-gray-400 mb-1.5 uppercase tracking-wider">{label}</label>
              <input type="password" value={value}
                onChange={e => setPwForm(f => ({ ...f, [field]: e.target.value }))} required
                className="w-full bg-surface border border-border rounded-xl px-4 py-3 text-white text-sm focus:outline-none focus:border-primary" />
            </div>
          ))}
          {pwMsg && (
            <div className={`p-3 rounded-lg text-sm ${pwMsg.type === 'success' ? 'bg-green-900/20 text-green-400' : 'bg-red-900/20 text-red-400'}`}>
              {pwMsg.text}
            </div>
          )}
          <button type="submit" disabled={pwLoading}
            className="px-6 py-2.5 bg-primary text-white rounded-xl text-sm font-medium hover:bg-primary/90 disabled:opacity-50 transition-colors">
            {pwLoading ? 'Changing...' : 'Change Password'}
          </button>
        </form>
      </div>
    </div>
  )
}
