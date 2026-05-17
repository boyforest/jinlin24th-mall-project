import { apiRequest } from '@/api/client'
import type { PageResult } from '@/api/product'

export interface OrderCreateDTO {
  receiverName: string
  receiverPhone: string
  receiverAddress: string
  remark?: string
  items: Array<{
    skuId: number
    quantity: number
  }>
}

export interface OrderVO {
  id: number
  orderNo: string
  recommenderUserId?: number | null
  level2RecommenderUserId?: number | null
  totalAmount?: number
  payAmount?: number
  freightAmount?: number
  discountAmount?: number
  status?: number
  receiverName?: string
  receiverPhone?: string
  receiverAddress?: string
  remark?: string
  payTime?: string
  deliveryTime?: string
  receiveTime?: string
  createTime?: string
  items?: Array<{
    productId: number
    skuId: number
    productName?: string
    skuName?: string
    productImage?: string
    price?: number
    quantity?: number
    totalPrice?: number
  }>
}

function buildQuery(params: Record<string, unknown> = {}) {
  const entries = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)

  return entries.length ? `?${entries.join('&')}` : ''
}

export function createOrder(data: OrderCreateDTO) {
  return apiRequest<OrderVO>('/user/order/create', { method: 'POST', data })
}

export function listOrders(params: { page?: number; size?: number; status?: number } = {}) {
  return apiRequest<PageResult<OrderVO>>(`/user/order/list${buildQuery(params)}`)
}

export function getOrder(id: number) {
  return apiRequest<OrderVO>(`/user/order/${id}`)
}

export function receiveOrder(id: number) {
  return apiRequest<OrderVO>(`/user/order/${id}/receive`, { method: 'POST' })
}
