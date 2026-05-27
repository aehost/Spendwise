import axios from 'axios'

// In production (Vercel), VITE_API_URL is set to the Railway gateway URL.
// In local dev, the Vite proxy rewrites /api → localhost:3000, so '/api' works.
const BASE_URL = import.meta.env.VITE_API_URL || '/api'

const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('sw_support_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

api.interceptors.response.use(
  res => res,
  async err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('sw_support_token')
      localStorage.removeItem('sw_support_user')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export const get = <T>(url: string, params?: Record<string, unknown>) =>
  api.get<{ data?: T }>(url, { params }).then(r => (r.data as any)?.data ?? r.data as T)

export const post = <T>(url: string, body?: unknown) =>
  api.post<{ data?: T }>(url, body).then(r => (r.data as any)?.data ?? r.data as T)

export const put = <T>(url: string, body?: unknown) =>
  api.put<{ data?: T }>(url, body).then(r => (r.data as any)?.data ?? r.data as T)

export const del = <T>(url: string) =>
  api.delete<{ data?: T }>(url).then(r => (r.data as any)?.data ?? r.data as T)

export default api
