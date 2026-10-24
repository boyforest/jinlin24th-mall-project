import { API_BASE_URL } from '@/config/app'
import { requireLogin } from '@/utils/auth'
import { currentPageUrl } from '@/utils/navigation'
import { clearToken, clearUserId, getToken } from '@/utils/storage'

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  auth?: boolean
  headers?: Record<string, string>
  redirectOnUnauthorized?: boolean
  showErrorToast?: boolean
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', data, auth = true, headers = {}, showErrorToast = false, redirectOnUnauthorized = true } = options

  const token = getToken()
  if (auth && !token) {
    throw new Error('请先登录')
  }

  const finalHeaders: Record<string, string> = { ...headers }
  if (auth && token) {
    finalHeaders.Authorization = `Bearer ${token}`
  }

  try {
    const res = await uni.request({
      url: joinUrl(API_BASE_URL, path),
      method,
      data,
      header: finalHeaders,
      timeout: 15000,
    })

    // uni.request 在不同平台返回结构略有差异，这里统一取 data
    const httpStatus = (res as any)?.statusCode ?? (res as any)?.[1]?.statusCode
    const body = (res as any)?.data ?? (res as any)?.[1]?.data

    if (httpStatus && httpStatus !== 200) {
      if (httpStatus === 401 && auth && getToken() === token) {
        clearToken()
        clearUserId()
        if (redirectOnUnauthorized) requireLogin(currentPageUrl())
      }
      throw new Error(body?.message || `HTTP ${httpStatus}`)
    }

    if (!body) {
      throw new Error('接口返回为空')
    }
    if (body.code !== 0 && body.code !== 200) {
      // 业务错误
      throw new Error(body.message || `业务错误 ${body.code}`)
    }
    return body.data
  } catch (error: any) {
    if (import.meta.env.DEV) {
      console.error('[API] 请求失败', {
        url: joinUrl(API_BASE_URL, path),
        method,
        error: error?.message || error,
        raw: error,
      })
    }
    if (showErrorToast) {
      uni.showToast({ title: error?.message || '请求失败', icon: 'none' })
    }
    throw error
  }
}

function joinUrl(base: string, path: string) {
  if (!base) return path
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  const a = base.endsWith('/') ? base.slice(0, -1) : base
  const b = path.startsWith('/') ? path : `/${path}`
  return a + b
}
