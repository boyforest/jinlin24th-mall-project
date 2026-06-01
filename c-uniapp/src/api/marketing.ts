import { apiRequest } from '@/api/client'
import { buildQuery } from '@/utils/query'

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

export function listMarketingActivities(position?: string) {
  return apiRequest<MarketingActivityVO[]>(`/user/marketing/activity/list${buildQuery({ position })}`, { auth: false })
}
