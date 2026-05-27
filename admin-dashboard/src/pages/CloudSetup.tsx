import { useState } from 'react'
import { CheckCircle, Copy, ExternalLink, Database, Key, Globe, Terminal, ArrowRight } from 'lucide-react'

const STEPS = [
  {
    title: 'Create a Supabase Project',
    icon: Globe,
    description: 'Sign up at supabase.com and create a new project.',
    actions: [
      'Go to https://supabase.com and click "Start your project"',
      'Sign in with GitHub',
      'Click "New Project" → choose your organization',
      'Set project name: spendwise-prod',
      'Set a strong database password (save it!)',
      'Choose region closest to your users (e.g. ap-south-1 for India)',
      'Click "Create new project" and wait ~2 minutes',
    ],
    code: null,
  },
  {
    title: 'Apply the Database Schema',
    icon: Database,
    description: 'Run the SpendWise schema SQL in Supabase SQL editor.',
    actions: [
      'In your Supabase project, go to SQL Editor (left sidebar)',
      'Click "New Query"',
      'Open database/schema.sql from this project',
      'Paste the entire content into the SQL editor',
      'Click "Run" (Ctrl+Enter)',
      'Verify tables are created in Table Editor',
    ],
    code: '-- Run in Supabase SQL Editor:\n-- Copy contents of database/schema.sql',
  },
  {
    title: 'Seed Admin User',
    icon: Key,
    description: 'Insert the default admin user into your cloud database.',
    actions: [
      'In SQL Editor, run the seed query below',
      'This creates admin@spendwise.app with password Admin@SpendWise2025',
      'Change the password immediately after first login',
    ],
    code: `INSERT INTO users (id, email, password_hash, name, role, is_verified, is_active)
VALUES (
  gen_random_uuid(),
  'admin@spendwise.app',
  '$2a$12$uKas5pi98cWITmmCLwKI6ORAyXX7WIKPsLRDPlOdeSd3v67oirKcy',
  'SpendWise Admin',
  'admin',
  TRUE,
  TRUE
) ON CONFLICT (email) DO NOTHING;`,
  },
  {
    title: 'Get Connection String',
    icon: Database,
    description: 'Copy your Supabase PostgreSQL connection string.',
    actions: [
      'In Supabase project → Settings → Database',
      'Scroll to "Connection string" section',
      'Select "URI" tab',
      'Copy the connection string (starts with postgres://)',
      'Replace [YOUR-PASSWORD] with your database password',
    ],
    code: 'postgresql://postgres:[YOUR-PASSWORD]@db.[PROJECT-REF].supabase.co:5432/postgres',
  },
  {
    title: 'Update Backend .env Files',
    icon: Terminal,
    description: 'Update the DATABASE_URL in all backend service .env files.',
    actions: [
      'Open backend/api-gateway/.env',
      'Replace DATABASE_URL with your Supabase connection string',
      'Do the same for backend/auth-service/.env',
      'Do the same for backend/transaction-service/.env',
      'Do the same for backend/user-service/.env',
      'Do the same for backend/analytics-service/.env',
      'Restart all backend services',
    ],
    code: `# In each backend service .env file:
DATABASE_URL=postgresql://postgres:[PASSWORD]@db.[PROJECT-REF].supabase.co:5432/postgres
NODE_ENV=production`,
  },
  {
    title: 'Update Android App',
    icon: Terminal,
    description: 'Point the Android app to your cloud backend.',
    actions: [
      'Open android/app/src/main/res/values/strings.xml (or NetworkModule.kt)',
      'Update BASE_URL to your deployed backend URL',
      'For local testing, use your machine\'s IP: http://192.168.x.x:3000',
      'For production, deploy backend to Railway/Render and use that URL',
      'Rebuild the Android app',
    ],
    code: `// In android/app/src/main/java/com/spendwise/di/NetworkModule.kt
private const val BASE_URL = "https://your-backend.railway.app/"
// OR for local testing:
private const val BASE_URL = "http://192.168.1.100:3000/"`,
  },
]

export default function CloudSetup() {
  const [completedSteps, setCompletedSteps] = useState<Set<number>>(new Set())
  const [copiedStep, setCopiedStep] = useState<number | null>(null)

  function toggleStep(i: number) {
    setCompletedSteps(prev => {
      const next = new Set(prev)
      next.has(i) ? next.delete(i) : next.add(i)
      return next
    })
  }

  function copyCode(code: string, i: number) {
    navigator.clipboard.writeText(code)
    setCopiedStep(i)
    setTimeout(() => setCopiedStep(null), 2000)
  }

  const progress = (completedSteps.size / STEPS.length) * 100

  return (
    <div className="p-6 max-w-4xl">
      <div className="flex items-center gap-3 mb-2">
        <div className="p-2 bg-primary/10 rounded-xl"><Database size={20} className="text-primary" /></div>
        <h1 className="text-2xl font-bold text-white">Connect to Cloud Database</h1>
      </div>
      <p className="text-gray-400 text-sm mb-6">
        Follow these 6 steps to migrate from local PostgreSQL to Supabase cloud database.
        Your data will be securely hosted and accessible from anywhere.
      </p>

      {/* Progress bar */}
      <div className="bg-card border border-border rounded-2xl p-4 mb-6">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm text-gray-400">Setup Progress</span>
          <span className="text-sm font-medium text-primary">{completedSteps.size}/{STEPS.length} steps complete</span>
        </div>
        <div className="w-full bg-surface rounded-full h-2">
          <div className="h-2 bg-primary rounded-full transition-all duration-500" style={{ width: `${progress}%` }} />
        </div>
      </div>

      {/* Quick links */}
      <div className="flex gap-3 mb-6 flex-wrap">
        {[
          { label: 'Supabase Dashboard', url: 'https://supabase.com/dashboard' },
          { label: 'Railway (Backend Hosting)', url: 'https://railway.app' },
          { label: 'Render (Alternative)', url: 'https://render.com' },
        ].map(({ label, url }) => (
          <a key={label} href={url} target="_blank" rel="noopener noreferrer"
            className="flex items-center gap-1.5 px-3 py-2 bg-card border border-border rounded-xl text-xs text-gray-400 hover:text-white hover:border-primary/50 transition-colors">
            <ExternalLink size={12} /> {label}
          </a>
        ))}
      </div>

      {/* Steps */}
      <div className="space-y-4">
        {STEPS.map((step, i) => {
          const done = completedSteps.has(i)
          const Icon = step.icon
          return (
            <div key={i} className={`bg-card border rounded-2xl overflow-hidden transition-all ${done ? 'border-green-500/30' : 'border-border'}`}>
              <div className="flex items-start gap-4 p-5">
                <button onClick={() => toggleStep(i)}
                  className={`w-8 h-8 rounded-full border-2 flex items-center justify-center flex-shrink-0 mt-0.5 transition-all
                    ${done ? 'bg-green-500 border-green-500' : 'border-gray-600 hover:border-primary'}`}>
                  {done ? <CheckCircle size={16} className="text-white" /> : <span className="text-xs font-bold text-gray-400">{i + 1}</span>}
                </button>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Icon size={16} className="text-primary" />
                    <h3 className="text-white font-semibold">{step.title}</h3>
                  </div>
                  <p className="text-sm text-gray-400 mb-4">{step.description}</p>

                  <ul className="space-y-1.5 mb-4">
                    {step.actions.map((action, j) => (
                      <li key={j} className="flex items-start gap-2 text-sm text-gray-300">
                        <ArrowRight size={12} className="text-primary mt-1 flex-shrink-0" />
                        {action}
                      </li>
                    ))}
                  </ul>

                  {step.code && (
                    <div className="relative">
                      <pre className="bg-surface border border-border rounded-xl p-4 text-xs text-gray-300 font-mono overflow-x-auto whitespace-pre-wrap">
                        {step.code}
                      </pre>
                      <button
                        onClick={() => copyCode(step.code!, i)}
                        className="absolute top-3 right-3 p-1.5 bg-card border border-border rounded-lg hover:border-primary transition-colors"
                        title="Copy to clipboard">
                        {copiedStep === i
                          ? <CheckCircle size={14} className="text-green-400" />
                          : <Copy size={14} className="text-gray-400" />}
                      </button>
                    </div>
                  )}
                </div>
              </div>

              {done && (
                <div className="bg-green-950/30 border-t border-green-500/20 px-5 py-2">
                  <span className="text-xs text-green-400 flex items-center gap-1"><CheckCircle size={12} /> Step completed</span>
                </div>
              )}
            </div>
          )
        })}
      </div>

      {completedSteps.size === STEPS.length && (
        <div className="mt-6 bg-green-950/30 border border-green-500/30 rounded-2xl p-6 text-center">
          <CheckCircle size={32} className="text-green-400 mx-auto mb-3" />
          <h3 className="text-lg font-bold text-white mb-1">🎉 Cloud Setup Complete!</h3>
          <p className="text-sm text-gray-400">Your SpendWise app is now connected to Supabase cloud database. Check the status banner in the sidebar to verify all services are online.</p>
        </div>
      )}
    </div>
  )
}
