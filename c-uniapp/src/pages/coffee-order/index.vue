<template>
  <view class="page business-page">
    <view class="ink-page-content">
      <view v-if="loading && !order" class="ink-loading">正在查阅咖啡订单…</view>
      <view v-if="error" class="ink-empty ink-card"><text class="ink-empty-title">订单暂时未能同步</text><text class="ink-empty-sub">{{ error }}</text><button class="ink-btn-secondary" :disabled="busy" @click="refresh">重新加载</button></view>
      <template v-if="order">
        <view class="card ink-card status-card"><text class="kicker">COFFEE · PICKUP</text><text class="status-title ink-title">{{ title }}</text><text class="status-description">{{ description }}</text><template v-if="showPickup"><text class="pickup-label">取餐号</text><text class="pickup-code">{{ order.pickupCode }}</text><text class="muted">{{ order.pickupDate }} · 请留意店员叫号</text></template><text v-else-if="order.paymentStatus !== 'PAID'" class="muted">支付后生成的有效取餐凭证将在这里展示</text><button class="refresh-state" :disabled="loading || busy" @click="refresh">{{ loading ? '同步中…' : '刷新订单状态' }}</button></view>
        <view class="card ink-card"><text class="section-title">{{ order.storeName }} · 到店自取</text><text class="muted">{{ store?.id === order.storeId ? store.address : '请到下单门店取餐' }}</text><text class="muted">{{ store?.id === order.storeId ? store.openingHours : '' }}</text></view>
        <view class="card ink-card"><text class="section-title">饮品明细 · {{ order.itemCount }} 杯</text><view v-for="(item, index) in order.items" :key="`${item.skuId}:${index}`" class="review-item"><image v-if="imageUrl(item.imageUrl)" :src="imageUrl(item.imageUrl)" class="cover" mode="aspectFill" /><view v-else class="cover placeholder">饮</view><view class="item-copy"><text class="item-name">{{ item.productName }}</text><text class="muted">{{ item.specName }} · {{ skuDescription(item) }}</text><text class="muted">¥{{ cents(item.unitPriceCent) }} × {{ item.quantity }}</text></view><text class="ink-price">¥{{ cents(item.lineTotalCent) }}</text></view><view class="total-row"><text>{{ order.paymentStatus === 'PAID' ? '实付' : '应付' }}</text><text class="ink-price">¥{{ cents(order.amounts.paidAmountCent ?? order.amounts.payableAmountCent) }}</text></view><view v-if="order.refundStatus === 'SUCCESS'" class="total-row"><text>已退回原支付账户</text><text class="ink-price">¥{{ cents(order.amounts.refundedAmountCent) }}</text></view><text v-if="order.refundReason" class="note">原因：{{ order.refundReason }}</text><text v-if="order.remark" class="note">备注：{{ order.remark }}</text></view>
        <view class="card ink-card"><view class="meta-row"><text>订单编号</text><text class="order-no">{{ order.orderNo }}</text></view><view class="meta-row"><text>下单时间</text><text>{{ formatTime(order.createdAt) }}</text></view><view v-if="order.paidAt" class="meta-row"><text>支付时间</text><text>{{ formatTime(order.paidAt) }}</text></view><text v-if="order.paymentStatus === 'PAID' && order.refundStatus === 'NONE'" class="note">如需修改或退款，请联系门店店员。制作状态以本页与店员确认为准。</text></view>
        <text v-if="actionMessage" class="action-message">{{ actionMessage }}</text>
        <button class="ink-btn-secondary all-orders" @click="allOrders">查看全部订单</button>
      </template>
    </view>
    <GlassActionBar v-if="order && unpaid && order.refundStatus === 'NONE'"><button class="ink-btn-secondary" :disabled="busy || loading || !!error" @click="cancel">取消订单</button><button class="ink-btn-primary pay-order" :disabled="busy || loading || !!error || !canPay" :loading="busy" @click="pay">{{ busy ? '正在处理…' : canPay ? '微信支付 ¥' + cents(order.amounts.payableAmountCent) : '暂时无法支付' }}</button></GlassActionBar>
  </view>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad, onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import { getCoffeeStore, type CoffeeStore } from '@/api/coffee'
import { getCoffeeOrder, payCoffeeOrder, cancelCoffeeOrder, type CoffeeOrder } from '@/api/coffeeOrder'
import { requestMiniAppPayment } from '@/api/payment'
import { getToken } from '@/utils/storage'
import { requireLogin } from '@/utils/auth'
import { useAuthStore } from '@/stores/auth'
import { cents, imageUrl, skuDescription, coffeeOrderingText } from '@/utils/coffee'
import GlassActionBar from '@/components/GlassActionBar.vue'
const order = ref<CoffeeOrder | null>(null), store = ref<CoffeeStore | null>(null)
const error = ref(''), loading = ref(false), busy = ref(false), actionMessage = ref('')
const auth = useAuthStore()
let id = '', visible = false, generation = 0, timer: ReturnType<typeof setTimeout> | undefined
const unpaid = computed(() => order.value?.fulfillmentStatus === 'WAIT_PAYMENT' && order.value.paymentStatus === 'UNPAID')
const canPay = computed(() => unpaid.value && store.value?.id === order.value?.storeId && !!store.value?.capabilities.payOnline)
const showPickup = computed(() => order.value?.paymentStatus === 'PAID' && order.value.refundStatus === 'NONE' && ['WAIT_ACCEPT', 'WAIT_MAKE', 'MAKING', 'READY'].includes(order.value.fulfillmentStatus))
const stageTitles = { WAIT_PAYMENT: '等待付款', WAIT_ACCEPT: '已付款 · 等待门店接单', WAIT_MAKE: '门店已接单', MAKING: '正在制作', READY: '做好啦，请来取餐', COMPLETED: '已取餐，愿您今日舒心', CANCELLED: '订单已取消', REJECTED: '门店未能接单' }
const title = computed(() => !order.value ? '' : ({ PENDING: '退款处理中', SUCCESS: '已原路退款', FAILED: '退款待门店核查' } as Record<string, string>)[order.value.refundStatus] || stageTitles[order.value.fulfillmentStatus])
const description = computed(() => {
  if (!order.value) return ''
  if (order.value.refundStatus === 'PENDING') return '退款申请已记录，到账结果以实际支付渠道为准。'
  if (order.value.refundStatus === 'SUCCESS') return '退款已成功，实际到账时间请以支付账户为准。'
  if (order.value.refundStatus === 'FAILED') return '退款需要进一步核查，请联系门店，暂勿重复付款。'
  return ({ WAIT_PAYMENT: '请在订单有效期内付款。如刚完成支付，请稍候或刷新状态。', WAIT_ACCEPT: '店员确认接单后开始安排制作，请稍候。', WAIT_MAKE: '已进入制作队列，请留意本页进度。', MAKING: '店员正在准备您的饮品。', READY: '请向店员出示本页取餐号，核对饮品后取走。', COMPLETED: '感谢光临，期待下次相见。', CANCELLED: '如已扣款，系统会继续核对并处理退款。', REJECTED: '请查看退款进度，或联系门店了解情况。' } as Record<string, string>)[order.value.fulfillmentStatus]
})
function formatTime(value: string) { return value?.replace('T', ' ').replace(/\+08:00$/, '') || '—' }
function stopTimer() { if (timer) clearTimeout(timer); timer = undefined }
function schedule() {
  stopTimer()
  if (visible && !error.value && order.value && (showPickup.value || unpaid.value || order.value.refundStatus === 'PENDING')) timer = setTimeout(refresh, 5000)
}
async function refresh() {
  if (!visible || !id || loading.value || busy.value || !requireLogin(`/pages/coffee-order/index?id=${encodeURIComponent(id)}`)) return
  const token = getToken(), current = ++generation
  stopTimer(); loading.value = true
  try {
    const value = await getCoffeeOrder(id)
    if (getToken() !== token || current !== generation) return
    order.value = value; error.value = ''; if (value.paymentStatus === 'PAID') actionMessage.value = ''
    // 详情不依赖门店目录是否开放；目录失败仅禁用付款，不遮住历史取餐信息。
    try { const result = await getCoffeeStore(); if (current === generation && getToken() === token) store.value = result }
    catch { if (current === generation) store.value = null }
  } catch (e: any) { if (getToken() === token && current === generation) error.value = e?.message || '请稍后重试' }
  finally { if (current === generation) { loading.value = false; schedule() } }
}
async function pay() {
  if (!order.value || !canPay.value || busy.value || loading.value || error.value) return
  const token = getToken()
  busy.value = true; stopTimer(); actionMessage.value = ''
  try {
    const latest = await getCoffeeStore()
    if (getToken() !== token) return
    store.value = latest
    if (!latest.capabilities.payOnline || latest.id !== order.value.storeId) throw new Error(coffeeOrderingText(latest))
    const params = await payCoffeeOrder(id)
    if (getToken() !== token) return
    await requestMiniAppPayment(params)
    if (getToken() === token) actionMessage.value = '支付操作已完成，正在核对到账结果…'
  } catch (e: any) {
    if (getToken() === token) actionMessage.value = String(e?.errMsg || e?.message || '').includes('cancel') ? '您已取消本次支付，可稍后继续。' : e?.message || '支付结果尚未确认，请刷新订单状态。'
  } finally { busy.value = false; if (getToken() === token) refresh() }
}
async function cancel() {
  if (!unpaid.value || busy.value || loading.value || error.value) return
  const token = getToken()
  busy.value = true; stopTimer()
  try {
    const result = await uni.showModal({ title: '取消这笔咖啡订单？', content: '仅取消当前未付款订单。若支付结果延迟到达，系统将按实际收款处理。', confirmText: '取消订单', cancelText: '继续保留' })
    if (!result.confirm || getToken() !== token) return
    await cancelCoffeeOrder(id)
    if (getToken() === token) actionMessage.value = '已提交取消，请以同步后的订单状态为准。'
  } catch (e: any) { if (getToken() === token) actionMessage.value = e?.message || '取消结果尚未确认，请刷新查看。' }
  finally { busy.value = false; if (getToken() === token) refresh() }
}
function allOrders() { uni.switchTab({ url: '/pages/orders/index' }) }
function hide() { visible = false; ++generation; loading.value = false; stopTimer() }
watch(() => auth.token, () => { ++generation; order.value = null; store.value = null; error.value = '登录状态已变化，请重新加载订单'; actionMessage.value = ''; loading.value = false; stopTimer(); if (visible) requireLogin(`/pages/coffee-order/index?id=${id}`) })
onLoad(options => { id = String(options?.id || ''); if (!/^\d+$/.test(id)) { id = ''; error.value = '订单编号无效，请从订单列表重新进入' } })
onShow(() => { visible = true; refresh() })
onHide(hide)
onUnload(hide)
onPullDownRefresh(async () => { await refresh(); uni.stopPullDownRefresh() })
</script>
<style scoped>
.card { padding: 28rpx; margin-bottom: 24rpx; }
.status-card { text-align: center; padding: 40rpx 28rpx; }
.kicker { display: block; color: #65755d; font-size: 19rpx; letter-spacing: 3rpx; }
.status-title { display: block; font-size: 36rpx; margin: 18rpx 0; }
.status-description { display: block; color: #526b43; font-size: 25rpx; line-height: 1.8; }
.pickup-label { display: block; margin-top: 32rpx; color: #65755d; font-size: 24rpx; }
.pickup-code { display: block; color: #315747; font-size: 100rpx; font-weight: 700; letter-spacing: 6rpx; margin: 8rpx 0; }
.muted { display: block; font-size: 23rpx; color: #65755d; line-height: 1.8; }
.section-title { display: block; font-size: 29rpx; font-weight: 600; margin-bottom: 18rpx; }
.review-item { display: flex; align-items: center; gap: 18rpx; margin: 24rpx 0; }
.cover { width: 100rpx; height: 116rpx; border-radius: 14rpx; flex: none; background: #e8efdf; }
.placeholder { display: flex; align-items: center; justify-content: center; color: #637f51; }
.item-copy { flex: 1; min-width: 0; }
.item-name { display: block; font-size: 28rpx; }
.total-row { display: flex; justify-content: space-between; padding: 20rpx 0 0; font-size: 26rpx; }
.note { display: block; font-size: 24rpx; color: #526b43; line-height: 1.8; margin-top: 20rpx; overflow-wrap: anywhere; }
.meta-row { display: flex; justify-content: space-between; gap: 20rpx; margin: 16rpx 0; font-size: 22rpx; color: #65755d; }
.order-no { text-align: right; max-width: 70%; overflow-wrap: anywhere; }
.refresh-state { background: transparent; color: #637f51; font-size: 24rpx; margin-top: 24rpx; }
.pay-order { flex: 1; }
.action-message { display: block; margin: 24rpx 0; line-height: 1.8; color: #526b43; font-size: 25rpx; }
.all-orders, .ink-empty button { margin-top: 24rpx; }
button[disabled] { opacity: .5; }
</style>
