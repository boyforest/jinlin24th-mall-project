import { PAGE_URLS } from '@/config/app'
import { getToken } from '@/utils/storage'

/**
 * C 端页面登录守卫。
 * <p>
 * 需要登录的页面或动作先调用该方法，未登录时跳转登录页。
 */
export function requireLogin(redirectUrl?: string): boolean {
  if (getToken()) {
    return true
  }
  const redirect = redirectUrl ? `?redirect=${encodeURIComponent(redirectUrl)}` : ''
  uni.navigateTo({ url: `${PAGE_URLS.login}${redirect}` })
  return false
}

/**
 * 格式化金额，避免页面里到处写 Number 兜底。
 */
export function money(value?: number | string | null): string {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}
