import { defineStore } from 'pinia'
import { addCart, deleteCart, listCart, updateCart, type CartVO } from '@/api/cart'
import { money, requireLogin } from '@/utils/auth'
import { getToken } from '@/utils/storage'

export interface CheckoutItem {
  skuId: number
  quantity: number
}

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [] as CartVO[],
    loading: false,
    error: '',
    pendingId: null as number | null,
    checkoutItems: [] as CheckoutItem[],
  }),
  getters: {
    busy: state => state.pendingId !== null,
    totalCount: state => state.items.reduce((sum, item) => sum + Number(item.quantity || 0), 0),
    checkedItems: state => state.items.filter(item => item.checked !== 0),
    checkedCount(): number {
      return this.checkedItems.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
    },
    checkedAmount(): number {
      // 以分计算，合计只使用后端按身份返回的成交价。
      return this.checkedItems.reduce((sum, item) => sum + Math.round(Number(item.effectivePrice ?? item.price ?? 0) * 100) * item.quantity, 0) / 100
    },
    checkedAmountText(): string { return money(this.checkedAmount) },
    allChecked(): boolean { return this.items.length > 0 && this.items.every(item => item.checked !== 0) },
    checkoutIssue(): string {
      if (this.loading || this.busy) return '正在更新养物，请稍候'
      if (this.error) return '请先重试，确认购物车信息'
      if (!this.checkedItems.length) return '请先选择养物'
      if (this.checkedItems.some(item => item.stock == null || item.quantity > item.stock || item.quantity < 1)) return '已选养物库存不足，请调整数量或取消勾选'
      return ''
    },
  },
  actions: {
    async refresh(_redirectUrl = '/pages/home/index') {
      if (!getToken()) { this.$reset(); return false }
      if (this.loading || this.busy) return false
      const token = getToken()
      this.loading = true
      this.error = ''
      try {
        const items = await listCart()
        if (getToken() !== token) { this.$reset(); return false }
        this.items = items || []
        return true
      } catch (e: any) {
        if (getToken() !== token) this.$reset()
        else this.error = e?.message || '养物暂时加载失败，请重试'
        return false
      } finally { this.loading = false }
    },
    /** 两个入口共用请求锁；请求成功后才提交本地状态，失败保留可重试状态。 */
    async mutate(id: number, operation: () => Promise<void>) {
      if (this.busy || this.loading) return false
      if (!requireLogin()) return false
      const token = getToken()
      this.pendingId = id
      try {
        await operation()
        if (getToken() !== token) { this.$reset(); return false }
        this.error = ''
        return true
      } catch (e: any) {
        if (getToken() === token) {
          this.error = '操作未完成，请重试以确认购物车信息'
          uni.showToast({ title: e?.message || '更新失败，请重试', icon: 'none' })
        } else this.$reset()
        return false
      } finally { this.pendingId = null }
    },
    async add(productId: number, skuId: number, quantity = 1, redirectUrl = '/pages/home/index') {
      if (!requireLogin(redirectUrl)) return false
      const token = getToken()
      const added = await this.mutate(0, async () => {
        await addCart({ productId, skuId, quantity, checked: 1 })
      })
      if (added && getToken() === token) await this.refresh()
      return added
    },
    async setQuantity(item: CartVO, quantity: number) {
      const next = Math.max(1, Math.floor(quantity))
      if (next === item.quantity || !Number.isFinite(next)) return false
      if (next > item.quantity && next > (item.stock ?? 0)) {
        uni.showToast({ title: '已达可售数量', icon: 'none' }); return false
      }
      const token = getToken()
      return this.mutate(item.id, async () => {
        const updated = await updateCart(item.id, { quantity: next, checked: item.checked })
        if (getToken() === token) this.items = this.items.map(row => row.id === item.id ? updated : row)
      })
    },
    async setChecked(item: CartVO, checked: number) {
      const token = getToken()
      return this.mutate(item.id, async () => {
        const updated = await updateCart(item.id, { checked })
        if (getToken() === token) this.items = this.items.map(row => row.id === item.id ? updated : row)
      })
    },
    async setAllChecked(checked: number) {
      const token = getToken()
      return this.mutate(-1, async () => {
        const results = await Promise.allSettled(this.items.filter(item => item.checked !== checked).map(item => updateCart(item.id, { checked })))
        // 即使部分失败也拉取实际结果，避免本地全选与服务端不一致。
        const items = await listCart()
        if (getToken() === token) this.items = items
        if (results.some(result => result.status === 'rejected')) throw new Error('部分养物未更新，请重试')
      })
    },
    async remove(id: number) {
      const token = getToken()
      return this.mutate(id, async () => {
        const removed = await deleteCart(id)
        if (!removed) throw new Error('移出失败，请重试')
        if (getToken() === token) this.items = this.items.filter(item => item.id !== id)
      })
    },
    prepareCheckout(redirectUrl: string): boolean {
      if (!requireLogin(redirectUrl)) return false
      if (this.checkoutIssue) { uni.showToast({ title: this.checkoutIssue, icon: 'none' }); return false }
      this.setCheckoutItems(this.checkedItems.map(({ skuId, quantity }) => ({ skuId, quantity })))
      return true
    },
    setCheckoutItems(items: CheckoutItem[]) { this.checkoutItems = items.map(item => ({ ...item })) },
    clearCheckoutItems() { this.checkoutItems = [] },
  },
})
