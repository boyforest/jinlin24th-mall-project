<template>
  <view class="page business-page">
    <view class="ink-page-content">
      <text class="kicker">COFFEE · PICKUP</text><text class="title ink-title">确认这一杯</text>
      <view v-if="loading" class="ink-loading">正在核对菜单与咖啡篮…</view>
      <view v-else-if="error" class="ink-empty ink-card"><text class="ink-empty-title">暂时无法结算</text><text class="ink-empty-sub">{{ error }}</text><button class="ink-btn-secondary" @click="load">重新核对</button><button class="ink-btn-secondary" @click="backToCart">返回咖啡篮</button></view>
      <template v-else-if="draft">
        <view class="card ink-card"><text class="section-title">{{ draft.store.name }} · 到店自取</text><text class="muted">{{ draft.store.address }}</text><text class="muted">{{ draft.store.openingHours }}</text><text class="note">请确认门店。支付后等待店员接单，再按取餐号到店取餐。</text></view>
        <view class="card ink-card"><text class="section-title">本次饮品 · 共 {{ count }} 杯</text><view v-for="row in draft.rows" :key="row.id" class="review-item"><image v-if="imageUrl(row.imageUrl)" :src="imageUrl(row.imageUrl)" class="cover" mode="aspectFill" /><view v-else class="cover placeholder">饮</view><view class="item-copy"><text class="item-name">{{ row.productName }}</text><text class="muted">{{ row.skuName }} · {{ skuDescription(row) }}</text><text class="muted">¥{{ cents(row.unitPriceCent) }} × {{ row.quantity }}</text></view><text class="ink-price">¥{{ cents(row.unitPriceCent * row.quantity) }}</text></view><view class="total-row"><text>应付合计</text><text class="ink-price">¥{{ cents(amount) }}</text></view></view>
        <view class="card ink-card"><text class="section-title">给店员的备注</text><textarea v-model="remark" :disabled="!!submitted" maxlength="255" placeholder="例如：请备一根吸管（选填）" class="remark" /><text class="muted">{{ remark.length }}/255 · 杯型、温度和糖度以所选规格为准</text></view>
        <view v-if="submitted" class="note">上次提交结果尚未确认。再次查询将使用原请求编号和相同内容，不会新建另一笔订单。</view>
        <view v-if="submitError" class="inline-error">{{ submitError }}</view>
      </template>
    </view>
    <GlassActionBar v-if="draft && !loading && !error"><view class="footer-copy"><text class="muted">到店自取 · {{ count }} 杯</text><text class="ink-price footer-price">¥{{ cents(amount) }}</text></view><button class="ink-btn-primary submit-order" :disabled="busy" :loading="busy" @click="submit">{{ busy ? '正在确认…' : submitted ? '查询原提交结果' : '确认并支付' }}</button></GlassActionBar>
  </view>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { getCoffeeStore, getCoffeeMenu, updateCoffeeCart, type CoffeeCartItem, type CoffeeStore } from '@/api/coffee'
import { createCoffeeOrder, payCoffeeOrder, type CoffeeOrderRequest } from '@/api/coffeeOrder'
import { requestMiniAppPayment } from '@/api/payment'
import { useCoffeeCartStore } from '@/stores/coffeeCart'
import { useBusinessContext } from '@/stores/businessContext'
import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/storage'
import { requireLogin } from '@/utils/auth'
import { cents, imageUrl, skuDescription, coffeeOrderingText } from '@/utils/coffee'
import GlassActionBar from '@/components/GlassActionBar.vue'
interface Draft { store: CoffeeStore; rows: CoffeeCartItem[]; request: CoffeeOrderRequest; createdId?: string }
const cart = useCoffeeCartStore(), context = useBusinessContext(), auth = useAuthStore()
const draft = ref<Draft | null>(null), submitted = ref(false), remark = ref('')
const loading = ref(false), error = ref(''), submitError = ref(''), busy = ref(false)
const amount = computed(() => (draft.value?.rows || []).reduce((sum, row) => sum + row.unitPriceCent * row.quantity, 0))
const count = computed(() => (draft.value?.rows || []).reduce((sum, row) => sum + row.quantity, 0))
let generation = 0, visible = false
function storageKey() { return `jl_coffee_checkout_${String(uni.getStorageSync('jl_user_id') || '')}` }
function requestKey() { return `coffee_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}` }
function backToCart() { context.cartBusiness = 'COFFEE'; uni.switchTab({ url: '/pages/cart/index' }) }
function showOrder(id: string, key: string, value: Draft) {
  uni.redirectTo({ url: `/pages/coffee-order/index?id=${encodeURIComponent(id)}`, success: () => {
    const saved = uni.getStorageSync(key)
    if (saved?.request?.requestKey === value.request.requestKey) uni.removeStorageSync(key)
  } })
}
async function load() {
  if (busy.value || loading.value || !requireLogin('/pages/coffee-checkout/index')) return
  const token = getToken(), current = ++generation, key = storageKey()
  loading.value = true; error.value = ''; submitError.value = ''
  try {
    const saved = uni.getStorageSync(key) as Draft | undefined
    if (saved?.request?.requestKey && saved.rows?.length && saved.store?.id) {
      draft.value = saved; submitted.value = true; remark.value = saved.request.remark
      if (saved.createdId) showOrder(saved.createdId, key, saved)
      return
    }
    const store = await getCoffeeStore()
    if (!store.capabilities.placeOrder || !store.capabilities.payOnline) throw new Error(coffeeOrderingText(store))
    const [menu, synced] = await Promise.all([getCoffeeMenu(store.id), cart.refresh(store.id)])
    if (getToken() !== token || generation !== current) return
    if (!synced) throw new Error(cart.error || '咖啡篮正在更新，请稍后重新核对')
    const rows = cart.items.filter(row => row.checked === 1)
    if (!rows.length) throw new Error('请先在咖啡篮勾选饮品')
    if (rows.some(row => !row.available)) throw new Error('已选饮品有售罄或下架，请返回咖啡篮调整')
    const selected = rows.map(row => {
      const product = menu.products.find(product => product.id === row.productId)
      const sku = product?.skus.find(sku => sku.id === row.skuId)
      if (!sku?.available) throw new Error('饮品规格已变化，请返回咖啡篮调整')
      return { ...row, productName: product!.name, imageUrl: product!.imageUrl, skuName: sku.name, temperature: sku.temperature, sugar: sku.sugar, unitPriceCent: sku.priceCent }
    })
    const total = selected.reduce((sum, row) => sum + row.quantity * row.unitPriceCent, 0)
    if (!Number.isSafeInteger(total) || total <= 0) throw new Error('金额暂时无法确认，请返回咖啡篮核对')
    context.setCoffeeStore(store)
    draft.value = { store, rows: selected, request: { storeId: store.id, menuVersion: menu.version, requestKey: requestKey(), items: selected.map(row => ({ skuId: row.skuId, quantity: row.quantity })), remark: '' } }
    submitted.value = false
  } catch (e: any) { if (getToken() === token && current === generation) { error.value = e?.message || '核对失败，请重试'; draft.value = null } }
  finally { if (current === generation) loading.value = false }
}
async function submit() {
  if (!draft.value || busy.value || loading.value || error.value || !requireLogin('/pages/coffee-checkout/index')) return
  const token = getToken(), key = storageKey(), value = draft.value
  busy.value = true; submitError.value = ''
  if (!submitted.value) value.request.remark = remark.value.trim()
  try {
    // 在发请求前保存相同请求键和不可变明细，超时/重启后由用户明确查询原提交。
    uni.setStorageSync(key, JSON.parse(JSON.stringify(value)))
    submitted.value = true
    const order = await createCoffeeOrder(value.request)
    value.createdId = order.id
    uni.setStorageSync(key, JSON.parse(JSON.stringify(value)))
    if (getToken() !== token) return
    // 只取消本次下单快照的勾选；版本变化的条目保留，不删除或覆盖其他设备的修改。
    await Promise.allSettled(value.rows.map(row => updateCoffeeCart(row.id, { checked: 0, expectedVersion: row.version })))
    if (getToken() !== token) return
    cart.refresh(value.store.id)
    try {
      if (order.paymentStatus === 'PAID' || order.fulfillmentStatus !== 'WAIT_PAYMENT') return
      if (order.amounts.payableAmountCent !== amount.value) {
        uni.showToast({ title: '订单金额已更新，请在订单内核对后付款', icon: 'none' }); return
      }
      const params = await payCoffeeOrder(order.id)
      if (getToken() !== token) return
      await requestMiniAppPayment(params)
      // 微信客户端返回不作为到账依据；详情页读取服务端状态。
    } catch (e: any) {
      if (getToken() === token) uni.showToast({ title: String(e?.errMsg || e?.message || '').includes('cancel') ? '支付已取消，可在订单中继续付款' : '支付结果待确认，请查看订单', icon: 'none', duration: 3000 })
    } finally { if (getToken() === token) showOrder(order.id, key, value) }
  } catch (e: any) {
    if (getToken() !== token) return
    if (e?.status && e.status >= 400 && e.status < 500) {
      uni.removeStorageSync(key); submitted.value = false
      error.value = e?.message || '菜单或门店已变化，请重新核对'
    } else submitError.value = '提交结果尚未确认，请查询原提交结果或到订单列表查看。请勿另外重复下单。'
  } finally { busy.value = false }
}
watch(() => auth.token, () => { ++generation; draft.value = null; error.value = '登录状态已变化，请重新核对'; remark.value = ''; loading.value = false; if (visible) requireLogin('/pages/coffee-checkout/index') })
onShow(() => { visible = true; load() })
onHide(() => { visible = false; ++generation; loading.value = false })
</script>
<style scoped>
.kicker { display: block; color: #65755d; font-size: 20rpx; letter-spacing: 3rpx; }
.title { display: block; font-size: 42rpx; margin: 12rpx 0 30rpx; }
.card { padding: 28rpx; margin-bottom: 24rpx; }
.section-title { display: block; font-size: 29rpx; font-weight: 600; margin-bottom: 18rpx; }
.muted { display: block; font-size: 23rpx; color: #65755d; line-height: 1.8; }
.note { font-size: 24rpx; color: #526b43; line-height: 1.8; margin-top: 18rpx; display: block; }
.review-item { display: flex; align-items: center; gap: 18rpx; margin: 24rpx 0; }
.cover { width: 100rpx; height: 116rpx; border-radius: 14rpx; flex: none; background: #e8efdf; }
.placeholder { display: flex; align-items: center; justify-content: center; color: #637f51; }
.item-copy, .footer-copy { flex: 1; min-width: 0; }
.item-name { display: block; font-size: 28rpx; }
.total-row { display: flex; justify-content: space-between; padding-top: 24rpx; border-top: 1rpx solid #e5ebdf; font-size: 27rpx; }
.remark { width: 100%; height: 150rpx; font-size: 26rpx; line-height: 1.7; }
.footer-price { font-size: 36rpx; }
.submit-order { min-width: 230rpx; flex: none; }
.inline-error { color: #9c4c3e; font-size: 25rpx; line-height: 1.8; }
.ink-empty button { margin-top: 24rpx; }
button[disabled] { opacity: .5; }
</style>
