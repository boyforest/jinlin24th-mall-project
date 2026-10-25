import { defineStore } from 'pinia'
import { addCoffeeCart, deleteCoffeeCart, listCoffeeCart, selectCoffeeCart, updateCoffeeCart, type CoffeeCartItem } from '@/api/coffee'
import { getToken } from '@/utils/storage'
import { requireLogin } from '@/utils/auth'
import { cents } from '@/utils/coffee'

export const useCoffeeCartStore = defineStore('coffeeCart', {
  state: () => ({ items: [] as CoffeeCartItem[], storeId: '', owner: '', loading: false, pending: '', error: '', revision: 0 }),
  getters: {
    busy: s => !!s.pending,
    totalCount: s => s.items.reduce((n, row) => n + row.quantity, 0),
    checkedItems: s => s.items.filter(row => row.checked === 1 && row.available),
    checkedCount(): number { return this.checkedItems.reduce((n, row) => n + row.quantity, 0) },
    checkedAmountCent(): number { return this.checkedItems.reduce((n, row) => n + row.unitPriceCent * row.quantity, 0) },
    checkedAmountText(): string { return cents(this.checkedAmountCent) },
    allChecked: s => s.items.some(row => row.available) && s.items.filter(row => row.available).every(row => row.checked === 1),
  },
  actions: {
    clear() {
      this.revision++
      this.items = []; this.owner = ''; this.loading = false; this.pending = ''; this.error = ''
    },
    syncAccount() {
      const token = getToken()
      if (this.owner !== token) { this.clear(); this.owner = token }
      return token
    },
    async refresh(storeId = '') {
      storeId = storeId || this.storeId
      const token = this.syncAccount()
      if (!token || !storeId || this.busy || this.loading) return false
      if (storeId !== this.storeId) { this.clear(); this.owner = token; this.storeId = storeId }
      const revision = ++this.revision
      this.loading = true; this.error = ''
      try {
        const rows = await listCoffeeCart(storeId)
        if (getToken() !== token || revision !== this.revision) return false
        this.items = rows
        return true
      } catch (e: any) {
        if (getToken() === token && revision === this.revision) this.error = e?.message || '咖啡篮加载失败，请重试'
        return false
      } finally {
        if (revision === this.revision) { this.loading = false; if (getToken() !== token) this.clear() }
      }
    },
    async mutate(key: string, operation: () => Promise<unknown>, storeId = '') {
      storeId = storeId || this.storeId
      const token = this.syncAccount()
      if (!token || this.busy || this.loading || !storeId) return false
      const revision = ++this.revision
      this.pending = key; this.storeId = storeId
      let ok = false
      let failure = ''
      try {
        await operation()
        ok = true
      } catch (e: any) {
        failure = e?.code === 90409 ? '购物篮已发生变化，已重新同步，请确认后再操作' : e?.message || '操作结果未确认，请查看同步后的购物篮'
      }
      // 包括超时的 POST 都只读回状态，绝不自动重放写请求。
      try {
        if (getToken() !== token || revision !== this.revision) return false
        const rows = await listCoffeeCart(storeId)
        if (getToken() !== token || revision !== this.revision) return false
        this.items = rows; this.error = ''
      } catch (e: any) {
        if (getToken() === token && revision === this.revision) this.error = e?.message || '购物篮未能同步，请重新加载'
        ok = false
      } finally {
        if (revision === this.revision) {
          this.pending = ''
          if (getToken() !== token) this.clear()
          else if (failure) uni.showToast({ title: failure, icon: 'none', duration: 3000 })
        }
      }
      return ok && getToken() === token && revision === this.revision
    },
    async add(storeId: string, skuId: string, quantity = 1) {
      if (!requireLogin('/pages/coffee/index')) return false
      return this.mutate('add', () => addCoffeeCart({ storeId, skuId, quantity }), storeId)
    },
    setQuantity(row: CoffeeCartItem, quantity: number) {
      if (!Number.isInteger(quantity) || quantity < 1 || quantity > 99 || quantity === row.quantity || (!row.available && quantity > row.quantity)) return Promise.resolve(false)
      return this.mutate(row.id, () => updateCoffeeCart(row.id, { quantity, expectedVersion: row.version }))
    },
    setChecked(row: CoffeeCartItem, checked: number) {
      if (!row.available && checked === 1) return Promise.resolve(false)
      return this.mutate(row.id, () => updateCoffeeCart(row.id, { checked, expectedVersion: row.version }))
    },
    setAllChecked(checked: number) { return this.mutate('selection', () => selectCoffeeCart(this.storeId, checked)) },
    remove(id: string) { return this.mutate(id, async () => { if (!await deleteCoffeeCart(id)) throw new Error('移出失败，请重试') }) },
  },
})
