import { apiRequest } from '@/api/client'
import { buildQuery } from '@/utils/query'
import { API_BASE_URL } from '@/config/app'

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface ProductVO {
  id: number
  categoryId?: number
  name: string
  mainImage?: string
  images?: string
  subtitle?: string
  detail?: string
  effects?: string
  precautions?: string
  sales?: number
  status?: number
}

export interface ProductSkuVO {
  id: number
  productId: number
  skuName: string
  /** 原价（仅供展示划线价，不要用它作为成交价） */
  price: number
  /** 会员价（仅供展示"分销会员 ¥X"的营销提示，不要用它作为成交价） */
  memberPrice?: number
  /**
   * 实际成交单价，由后端按购买者身份算好。
   * <p>
   * **显示与合计一律用它**，不要在 price / memberPrice 之间自行判断：
   * 会员价只对分销会员生效，前端自行判断会导致"显示 A 价、结算 B 价"。
   */
  effectivePrice?: number
  stock?: number
  skuImage?: string
  status?: number
}

function normalizeAssetUrl(url?: string) {
  if (!url) return url
  if (url.startsWith('/uploads/')) {
    return `${API_BASE_URL}${url}`
  }
  return url.replace(/^https?:\/\/localhost:7878/i, API_BASE_URL)
}

function normalizeCsvUrls(value?: string) {
  if (!value) return value
  return value
    .split(',')
    .map(item => normalizeAssetUrl(item.trim()) || '')
    .filter(Boolean)
    .join(',')
}

function normalizeProduct<T extends ProductVO>(product: T): T {
  return {
    ...product,
    mainImage: normalizeAssetUrl(product.mainImage),
    images: normalizeCsvUrls(product.images),
  }
}

export function listProducts(params: { page?: number; size?: number; categoryId?: number; keyword?: string } = {}) {
  return apiRequest<PageResult<ProductVO>>(`/user/product/list${buildQuery(params)}`, { auth: false }).then((data) => ({
    ...data,
    records: (data?.records || []).map(normalizeProduct),
  }))
}

export function getProduct(id: number) {
  return apiRequest<ProductVO>(`/user/product/${id}`, { auth: false }).then(normalizeProduct)
}

export function listProductSkus(id: number) {
  return apiRequest<ProductSkuVO[]>(`/user/product/${id}/skus`, { auth: false })
}
