import { apiRequest } from '@/api/client'
import { buildQuery } from '@/utils/query'
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

export function createOrder(data: OrderCreateDTO) {
  return apiRequest<OrderVO>('/user/order/create', { method: 'POST', data })
}

export function listOrders(params: { page?: number; size?: number; status?: number } = {}) {
  return apiRequest<PageResult<OrderVO>>(`/user/order/list${buildQuery(params)}`)
}

export function getOrder(id: number | string) {
  return apiRequest<OrderVO>(`/user/order/${id}`)
}

export function receiveOrder(id: number | string) {
  return apiRequest<OrderVO>(`/user/order/${id}/receive`, { method: 'POST' })
}

export interface OrderPreviewVO {
  items: Array<NonNullable<OrderVO['items']>[number] & { stock: number; quantity: number }>
  totalAmount: number
  freightAmount: number
  discountAmount: number
  payAmount: number
}

export function previewOrder(items: OrderCreateDTO['items']) {
  return apiRequest<OrderPreviewVO>('/user/order/preview', { method: 'POST', data: { items } })
}
