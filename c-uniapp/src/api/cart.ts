import { apiRequest } from '@/api/client'

export interface CartVO {
  id: number
  userId: number
  productId: number
  skuId: number
  quantity: number
  checked: number
  productName?: string
  productMainImage?: string
  skuName?: string
  skuImage?: string
  /** 原价（仅供展示划线价） */
  price?: number
  /** 会员价（仅供展示营销提示） */
  memberPrice?: number
  /** 实际成交单价（后端按身份算好），显示与合计一律用它 */
  effectivePrice?: number
  stock?: number
}

export interface CartDTO {
  productId: number
  skuId: number
  quantity: number
  checked?: number
}

export function listCart() {
  return apiRequest<CartVO[]>('/user/cart/list', { redirectOnUnauthorized: false })
}

export function addCart(data: CartDTO) {
  return apiRequest<CartVO>('/user/cart', { method: 'POST', data })
}

export function updateCart(id: number, data: Partial<CartDTO>) {
  return apiRequest<CartVO>(`/user/cart/${id}`, { method: 'PUT', data })
}

export function deleteCart(id: number) {
  return apiRequest<boolean>(`/user/cart/${id}`, { method: 'DELETE' })
}
