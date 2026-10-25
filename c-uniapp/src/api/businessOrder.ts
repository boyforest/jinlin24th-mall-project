import { apiRequest } from './client'
import type { BusinessType } from '@/stores/businessContext'
import type { BusinessOrderStatus, StatusGroup } from '@/constants/businessOrderStatus'
export interface BusinessOrderSummary {
  businessType: BusinessType; id: string; orderNo: string; storeId: string | null; storeName: string
  fulfillmentType: 'DELIVERY' | 'PICKUP'; status: BusinessOrderStatus; createdAt: string; itemCount: number
  summaryItems: { productId: string; skuId: string; productName: string; specName: string; imageUrl: string | null; unitPriceCent: number; quantity: number; lineTotalCent: number }[]
  amounts: { totalAmountCent: number; discountAmountCent: number; freightAmountCent: number; payableAmountCent: number; paidAmountCent: number | null }
  pickupCode: string | null; pickupDate: string | null
}
export interface BusinessOrderPage { records: BusinessOrderSummary[]; total: number; size: number; current: number; pages: number }
export function listBusinessOrders(data: { page: number; size: number; businessType?: BusinessType; statusGroup?: StatusGroup; status?: BusinessOrderStatus }) {
  // uni.request serializes undefined query values as empty strings. Omitted filters
  // must stay absent so the server can apply its default business/status scope.
  const query = Object.fromEntries(Object.entries(data).filter(([, value]) => value !== undefined))
  return apiRequest<BusinessOrderPage>('/user/business-order/list', { data: query })
}
