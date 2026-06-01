import { PAGE_URLS } from '@/config/app'
import { getToken } from '@/utils/storage'

export { money } from '@/utils/format'

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
