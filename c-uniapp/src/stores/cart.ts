import { defineStore } from 'pinia'
import { addCart, deleteCart, listCart, updateCart, type CartVO } from '@/api/cart'
import { money, requireLogin } from '@/utils/auth'

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [] as CartVO[],
    loading: false,
  }),
  getters: {
    totalCount: (state) => state.items.reduce((sum, item) => sum + Number(item.quantity || 0), 0),
    checkedItems: (state) => state.items.filter((item) => item.checked !== 0),
    checkedCount(): number {
      return this.checkedItems.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
    },
    checkedAmount(): number {
      return this.checkedItems.reduce(
        (sum, item) => sum + Number(item.memberPrice || item.price || 0) * Number(item.quantity || 0),
        0,
      )
    },
    checkedAmountText(): string {
      return money(this.checkedAmount)
    },
    allChecked(): boolean {
      return this.items.length > 0 && this.items.every((item) => item.checked !== 0)
    },
  },
  actions: {
    async refresh(redirectUrl = '/pages/home/index') {
      if (!requireLogin(redirectUrl)) return
      this.loading = true
      try {
        this.items = await listCart()
      } finally {
        this.loading = false
      }
    },
    async add(productId: number, skuId: number, quantity = 1, redirectUrl = '/pages/home/index') {
      if (!requireLogin(redirectUrl)) return false
      await addCart({ productId, skuId, quantity, checked: 1 })
      await this.refresh(redirectUrl)
      return true
    },
    async setQuantity(item: CartVO, quantity: number) {
      const next = Math.max(1, quantity)
      await updateCart(item.id, { quantity: next, checked: item.checked })
      item.quantity = next
    },
    async setChecked(item: CartVO, checked: number) {
      await updateCart(item.id, { quantity: item.quantity, checked })
      item.checked = checked
    },
    async setAllChecked(checked: number) {
      await Promise.all(this.items.map((item) => updateCart(item.id, { quantity: item.quantity, checked })))
      this.items.forEach((item) => {
        item.checked = checked
      })
    },
    async remove(id: number) {
      await deleteCart(id)
      this.items = this.items.filter((item) => item.id !== id)
    },
  },
})
