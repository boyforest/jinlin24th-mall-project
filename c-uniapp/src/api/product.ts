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

export function listProducts(params: { page?: number; size?: number; categoryId?: number } = {}) {
  const query = new URLSearchParams()
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))
  if (params.categoryId) query.set('categoryId', String(params.categoryId))
  const qs = query.toString()
  return apiRequest<PageResult<ProductVO>>(`/user/product/list${qs ? `?${qs}` : ''}`, { auth: false })
}

export function getProduct(id: number) {
  return apiRequest<ProductVO>(`/user/product/${id}`, { auth: false })
}

export function listProductSkus(id: number) {
  return apiRequest<ProductSkuVO[]>(`/user/product/${id}/skus`, { auth: false })
}
