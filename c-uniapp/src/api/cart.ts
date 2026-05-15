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
  price?: number
  memberPrice?: number
  stock?: number
}

export interface CartDTO {
  productId: number
  skuId: number
  quantity: number
  checked?: number
}

export function listCart() {
  return apiRequest<CartVO[]>('/user/cart/list')
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
