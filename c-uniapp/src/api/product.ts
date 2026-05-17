import { apiRequest } from '@/api/client'

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

export function listProducts(params: { page?: number; size?: number; categoryId?: number; keyword?: string } = {}) {
  return apiRequest<PageResult<ProductVO>>(`/user/product/list${buildQuery(params)}`, { auth: false })
}

export function getProduct(id: number) {
  return apiRequest<ProductVO>(`/user/product/${id}`, { auth: false })
}

export function listProductSkus(id: number) {
  return apiRequest<ProductSkuVO[]>(`/user/product/${id}/skus`, { auth: false })
}
