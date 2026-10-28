import { apiRequest } from './client'
import type { WxPayParamsVO } from './payment'
export interface CoffeeOrderRequest {
  storeId: string
  requestKey: string
  menuVersion: string
  items: { skuId: string; quantity: number }[]
  remark: string
}
export interface CoffeeOrder {
  id: string; orderNo: string; storeId: string; storeName: string
  pickupCode: string; pickupDate: string
  fulfillmentStatus: 'WAIT_PAYMENT' | 'WAIT_ACCEPT' | 'WAIT_MAKE' | 'MAKING' | 'READY' | 'COMPLETED' | 'CANCELLED' | 'REJECTED'
  paymentStatus: 'UNPAID' | 'PAID' | 'CLOSED'
  refundStatus: 'NONE' | 'PENDING' | 'SUCCESS' | 'FAILED'
  refundReason: string | null; remark: string | null; createdAt: string; paidAt: string | null
  itemCount: number; notificationStatus: string
  items: { productId: string; skuId: string; productName: string; specName: string; imageUrl: string | null; temperature: 'HOT' | 'ICED'; sugar: 'REGULAR' | 'LESS' | 'NONE'; unitPriceCent: number; quantity: number; lineTotalCent: number }[]
  amounts: { totalAmountCent: number; payableAmountCent: number; paidAmountCent: number | null; refundedAmountCent: number }
}
const root = '/user/coffee/orders'
export const createCoffeeOrder = (data: CoffeeOrderRequest) => apiRequest<CoffeeOrder>(root, { method: 'POST', data, redirectOnUnauthorized: false })
export const getCoffeeOrder = (id: string) => apiRequest<CoffeeOrder>(`${root}/${encodeURIComponent(id)}`, { redirectOnUnauthorized: false })
export const payCoffeeOrder = (id: string) => apiRequest<WxPayParamsVO>(`${root}/${encodeURIComponent(id)}/payment`, { method: 'POST', redirectOnUnauthorized: false })
export const cancelCoffeeOrder = (id: string) => apiRequest<CoffeeOrder>(`${root}/${encodeURIComponent(id)}/cancel`, { method: 'POST', redirectOnUnauthorized: false })
