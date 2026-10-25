import { defineStore } from 'pinia'
import type { CoffeeStore } from '@/api/coffee'
export type BusinessType = 'GOODS' | 'COFFEE'
const storeIdKey = 'jl_coffee_store_id'
function rememberedStoreId(): string {
  const value = uni.getStorageSync(storeIdKey)
  return typeof value === 'string' && /^\d+$/.test(value) ? value : ''
}
export const useBusinessContext = defineStore('businessContext', {
  state: () => ({ cartBusiness: 'GOODS' as BusinessType, coffeeStore: null as CoffeeStore | null, coffeeStoreId: rememberedStoreId() }),
  actions: {
    setCoffeeStore(store: CoffeeStore) {
      this.coffeeStore = store
      this.coffeeStoreId = store.id
      // Public store identity only; no token or account-related cart data is persisted.
      uni.setStorageSync(storeIdKey, store.id)
    },
  },
})
