import { defineStore } from 'pinia'
import { addCart, deleteCart, listCart, updateCart, type CartVO } from '@/api/cart'
import { money, requireLogin } from '@/utils/auth'

export interface CheckoutItem {
  skuId: number
  quantity: number
}

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [] as CartVO[],
    loading: false,
    checkoutItems: [] as CheckoutItem[],
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
      const results = await Promise.allSettled(
        this.items.map((item) => updateCart(item.id, { quantity: item.quantity, checked }))
      )
      const failed = results.filter((r) => r.status === 'rejected')
      if (failed.length > 0) {
        // 部分更新失败，重新拉取购物车以对齐服务端状态
        console.warn(`全选更新中 ${failed.length}/${this.items.length} 项失败，重新同步购物车`)
        await this.refresh()
      } else {
        this.items.forEach((item) => {
          item.checked = checked
        })
      }
    },
    async remove(id: number) {
      await deleteCart(id)
      this.items = this.items.filter((item) => item.id !== id)
    },
    setCheckoutItems(items: CheckoutItem[]) {
      this.checkoutItems = items
    },
    clearCheckoutItems() {
      this.checkoutItems = []
    },
  },
})
