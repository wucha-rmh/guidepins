import { apiRequest } from './client'
import type { AccountIdentity, Credentials } from '../types/auth'

function identity(data: unknown): AccountIdentity {
  if (typeof data !== 'object' || data === null) throw new Error('账号数据格式有误，请重试。')
  const user = data as Partial<AccountIdentity>
  if (typeof user.id !== 'string' || !user.id || !['user', 'admin'].includes(user.role ?? '') ||
    !(user.email === null || typeof user.email === 'string') ||
    !(user.phone === null || typeof user.phone === 'string') ||
    !(user.avatarPath === null || typeof user.avatarPath === 'string') ||
    typeof user.createdAt !== 'string' || !Number.isFinite(Date.parse(user.createdAt))) {
    throw new Error('账号数据格式有误，请重试。')
  }
  return user as AccountIdentity
}

export async function currentAccount(signal?: AbortSignal) {
  return identity(await apiRequest('/api/auth/me', { signal }))
}

export async function loginAccount(body: Credentials) {
  return identity(await apiRequest('/api/auth/login', { method: 'POST', body }))
}

export async function registerAccount(body: Credentials) {
  return identity(await apiRequest('/api/auth/register', { method: 'POST', body }))
}

export async function logoutAccount() {
  const result = await apiRequest('/api/auth/logout', { method: 'POST' })
  if (result !== null) throw new Error('退出响应格式有误，请重新确认登录状态。')
}
