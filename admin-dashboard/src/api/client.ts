import axios from 'axios'

const API_URL = import.meta.env.VITE_API_URL || '/api'

export const api = axios.create({ baseURL: API_URL })

api.interceptors.request.use(config => {
  const token = localStorage.getItem('sw_admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  r => r,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('sw_admin_token')
      localStorage.removeItem('sw_admin_user')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export interface ApiOk<T> { success: true; data: T }

export async function get<T>(url: string, params?: object): Promise<T> {
  const r = await api.get<ApiOk<T>>(url, { params })
  return r.data.data as T
}

export async function post<T>(url: string, data?: object): Promise<T> {
  const r = await api.post<ApiOk<T>>(url, data)
  return r.data.data as T
}

export async function put<T>(url: string, data?: object): Promise<T> {
  const r = await api.put<ApiOk<T>>(url, data)
  return r.data.data as T
}

export async function del<T>(url: string): Promise<T> {
  const r = await api.delete<ApiOk<T>>(url)
  return r.data.data as T
}
