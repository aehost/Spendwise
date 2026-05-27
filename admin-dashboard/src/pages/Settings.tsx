import { useState } from 'react'
import { post } from '../api/client'
import { UserPlus, Key, User, CheckCircle } from 'lucide-react'

export default function Settings() {
  const adminUser = JSON.parse(localStorage.getItem('sw_admin_user') || '{}')

  // Create support agent
  const [agentForm, setAgentForm] = useState({ email: '', name: '', password: '' })
  const [agentLoading, setAgentLoading] = useState(false)
  const [agentMsg, setAgentMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  // Change password
  const [pwForm, setPwForm] = useState({ current: '', newPw: '', confirm: '' })
  const [pwLoading, setPwLoading] = useState(false)
  const [pwMsg, setPwMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  async function createAgent(e: React.FormEvent) {
    e.preventDefault()
    setAgentLoading(true); setAgentMsg(null)
    try {
      await post('/admin/support-agents', { ...agentForm })
      setAgentMsg({ type: 'success', text: `Support agent ${agentForm.email} created successfully` })
      setAgentForm({ email: '', name: '', password: '' })
    } catch (err: any) {
      setAgentMsg({ type: 'error', text: err.response?.data?.error || 'Failed to create agent' })
    } finally { setAgentLoading(false) }
  }

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
    <div className="p-6 max-w-2xl">
      <h1 className="text-2xl font-bold text-white mb-6">Settings</h1>

      {/* Admin Profile */}
      <div className="bg-card border border-border rounded-2xl p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <User size={16} className="text-primary" />
          <h2 className="text-base font-semibold text-white">Admin Profile</h2>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <div className="text-xs text-gray-500 mb-1">Email</div>
            <div className="text-sm text-white bg-surface rounded-lg px-3 py-2">{adminUser.email || '—'}</div>
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">Role</div>
            <div className="text-sm text-purple-300 bg-purple-900/20 rounded-lg px-3 py-2 capitalize">{adminUser.role || '—'}</div>
          </div>
        </div>
      </div>

      {/* Change Password */}
      <div className="bg-card border border-border rounded-2xl p-6 mb-6">
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

      {/* Create Support Agent (admin only) */}
      {adminUser.role === 'admin' && (
        <div className="bg-card border border-border rounded-2xl p-6">
          <div className="flex items-center gap-3 mb-4">
            <UserPlus size={16} className="text-green-400" />
            <h2 className="text-base font-semibold text-white">Create Support Agent</h2>
          </div>
          <p className="text-xs text-gray-500 mb-4">Create a new support account. The agent will be able to manage tickets and look up user information.</p>
          <form onSubmit={createAgent} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-400 mb-1.5 uppercase tracking-wider">Full Name</label>
                <input type="text" value={agentForm.name}
                  onChange={e => setAgentForm(f => ({ ...f, name: e.target.value }))} required
                  className="w-full bg-surface border border-border rounded-xl px-4 py-3 text-white text-sm focus:outline-none focus:border-primary" />
              </div>
              <div>
                <label className="block text-xs text-gray-400 mb-1.5 uppercase tracking-wider">Email</label>
                <input type="email" value={agentForm.email}
                  onChange={e => setAgentForm(f => ({ ...f, email: e.target.value }))} required
                  className="w-full bg-surface border border-border rounded-xl px-4 py-3 text-white text-sm focus:outline-none focus:border-primary" />
              </div>
            </div>
            <div>
              <label className="block text-xs text-gray-400 mb-1.5 uppercase tracking-wider">Temporary Password</label>
              <input type="password" value={agentForm.password}
                onChange={e => setAgentForm(f => ({ ...f, password: e.target.value }))} required minLength={8}
                className="w-full bg-surface border border-border rounded-xl px-4 py-3 text-white text-sm focus:outline-none focus:border-primary" />
            </div>
            {agentMsg && (
              <div className={`p-3 rounded-lg text-sm flex items-center gap-2 ${agentMsg.type === 'success' ? 'bg-green-900/20 text-green-400' : 'bg-red-900/20 text-red-400'}`}>
                {agentMsg.type === 'success' && <CheckCircle size={14} />}
                {agentMsg.text}
              </div>
            )}
            <button type="submit" disabled={agentLoading}
              className="px-6 py-2.5 bg-green-600 hover:bg-green-700 text-white rounded-xl text-sm font-medium disabled:opacity-50 transition-colors">
              {agentLoading ? 'Creating...' : 'Create Support Agent'}
            </button>
          </form>
        </div>
      )}
    </div>
  )
}
