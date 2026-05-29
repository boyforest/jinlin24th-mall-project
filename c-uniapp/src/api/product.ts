import { apiRequest } from '@/api/client'
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
  price: number
  memberPrice?: number
  stock?: number
  skuImage?: string
  status?: number
}

function buildQuery(params: Record<string, unknown> = {}) {
  const entries = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)

  return entries.length ? `?${entries.join('&')}` : ''
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
