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
  const query = new URLSearchParams()
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))
  if (params.status !== undefined) query.set('status', String(params.status))
  const qs = query.toString()
  return apiRequest<PageResult<OrderVO>>(`/user/order/list${qs ? `?${qs}` : ''}`)
}

export function getOrder(id: number) {
  return apiRequest<OrderVO>(`/user/order/${id}`)
}
