import { apiRequest } from './client'
export type Temperature = 'HOT' | 'ICED'
export type Sugar = 'REGULAR' | 'LESS' | 'NONE'
export interface CoffeeStore {
  id: string; name: string; address: string; openingHours: string; timeZone: 'Asia/Shanghai'
  businessStatus: 'OPEN' | 'CLOSED' | 'PAUSED'; announcement: string | null
  capabilities: { browse: boolean; addToCart: boolean; placeOrder: boolean; payOnline: boolean }
  orderUnavailableReason: string | null
}
export interface CoffeeSku {
  id: string; productId: string; name: string; temperature: Temperature; sugar: Sugar
  priceCent: number; available: boolean; unavailableReason: string | null
}
export interface CoffeeProduct {
  id: string; categoryId: string; name: string; imageUrl: string | null; description: string; sort: number; skus: CoffeeSku[]
}
export interface CoffeeMenu {
  storeId: string; version: string; categories: { id: string; name: string; sort: number }[]; products: CoffeeProduct[]
}
export interface CoffeeCartItem {
  id: string; storeId: string; productId: string; skuId: string; productName: string; skuName: string
  imageUrl: string | null; temperature: Temperature; sugar: Sugar; unitPriceCent: number
  quantity: number; checked: number; available: boolean; unavailableReason: string | null; version: number
}
const root = '/user/coffee'
export const getCoffeeStore = () => apiRequest<CoffeeStore>(`${root}/store`, { auth: false })
export const getCoffeeMenu = (storeId: string) => apiRequest<CoffeeMenu>(`${root}/menu`, { auth: false, data: { storeId } })
export const listCoffeeCart = (storeId: string) => apiRequest<CoffeeCartItem[]>(`${root}/cart/list`, { data: { storeId }, redirectOnUnauthorized: false })
export const addCoffeeCart = (data: { storeId: string; skuId: string; quantity: number; checked?: number }) => apiRequest<CoffeeCartItem>(`${root}/cart`, { method: 'POST', data, redirectOnUnauthorized: false })
export const updateCoffeeCart = (id: string, data: { quantity?: number; checked?: number; expectedVersion: number }) => apiRequest<CoffeeCartItem>(`${root}/cart/${encodeURIComponent(id)}`, { method: 'PUT', data, redirectOnUnauthorized: false })
export const selectCoffeeCart = (storeId: string, checked: number) => apiRequest<CoffeeCartItem[]>(`${root}/cart/selection`, { method: 'PUT', data: { storeId, checked }, redirectOnUnauthorized: false })
export const deleteCoffeeCart = (id: string) => apiRequest<boolean>(`${root}/cart/${encodeURIComponent(id)}`, { method: 'DELETE', redirectOnUnauthorized: false })
