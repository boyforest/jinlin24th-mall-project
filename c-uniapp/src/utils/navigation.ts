import { PAGE_URLS } from '@/config/app'

export function safeNavigateBack(fallbackUrl: string = PAGE_URLS.home) {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.switchTab({ url: fallbackUrl })
}

export function currentPageUrl(): string {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as any
  if (!page?.route) return PAGE_URLS.home
  const query = Object.entries(page.options || {})
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`).join('&')
  return `/${page.route}${query ? `?${query}` : ''}`
}

/** 返回仍在栈内的原页面，保留滚动、已选规格和搜索结果。 */
export function canReturnToPrevious(url: string): boolean {
  const pages = getCurrentPages()
  const previous = pages[pages.length - 2] as any
  if (!previous) return false
  const [path, query = ''] = url.split('?')
  if (`/${previous.route}` !== path) return false
  return query.split('&').filter(Boolean).every(pair => {
    const [key, value = ''] = pair.split('=')
    return String(previous.options?.[decodeURIComponent(key)] ?? '') === decodeURIComponent(value)
  })
}
