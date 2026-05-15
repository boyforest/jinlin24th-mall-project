import { STORAGE_KEYS } from '@/config/app'

export function getToken(): string {
  return (uni.getStorageSync(STORAGE_KEYS.token) as string) || ''
}

export function setToken(token: string) {
  uni.setStorageSync(STORAGE_KEYS.token, token || '')
}

export function clearToken() {
  uni.removeStorageSync(STORAGE_KEYS.token)
}

export function getUserId(): number | null {
  const v = uni.getStorageSync(STORAGE_KEYS.userId)
  if (v === undefined || v === null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

export function setUserId(userId: number | string) {
  uni.setStorageSync(STORAGE_KEYS.userId, String(userId))
}

export function clearUserId() {
  uni.removeStorageSync(STORAGE_KEYS.userId)
}

export function getInviterUserId(): number | null {
  const v = uni.getStorageSync(STORAGE_KEYS.inviterUserId)
  if (v === undefined || v === null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

export function setInviterUserId(inviterUserId: number | string) {
  uni.setStorageSync(STORAGE_KEYS.inviterUserId, String(inviterUserId))
}

export function clearInviterUserId() {
  uni.removeStorageSync(STORAGE_KEYS.inviterUserId)
}

