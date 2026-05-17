import { apiRequest } from '@/api/client'

export interface MarketingActivityVO {
  id: number
  title: string
  subtitle?: string
  imageUrl?: string
  content?: string
  position?: string
  linkType?: 'none' | 'product' | 'category' | 'page' | string
  linkValue?: string
  status?: number
  sort?: number
}

function buildQuery(params: Record<string, unknown> = {}) {
  const entries = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)

  return entries.length ? `?${entries.join('&')}` : ''
}

export function listMarketingActivities(position?: string) {
  return apiRequest<MarketingActivityVO[]>(`/user/marketing/activity/list${buildQuery({ position })}`, { auth: false })
}
