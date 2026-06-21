import { PAGE_URLS } from '@/config/app'

export function safeNavigateBack(fallbackUrl: string = PAGE_URLS.home) {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.switchTab({ url: fallbackUrl })
}
