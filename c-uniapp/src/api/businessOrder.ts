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
export const listBusinessOrders = (data: { page: number; size: number; businessType?: BusinessType; statusGroup?: StatusGroup; status?: BusinessOrderStatus }) => apiRequest<BusinessOrderPage>('/user/business-order/list', { data })
