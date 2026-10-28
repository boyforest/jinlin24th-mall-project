<template>
  <view class="page business-page">
    <view class="ink-page-content">
      <view class="page-head"><text class="title ink-title">我的订单</text><text class="page-description">养物与日常，悉心收好</text></view>
      <BusinessTabs :model-value="business" :tabs="businessTabs" @update:model-value="selectBusiness" />
      <scroll-view scroll-x class="status-scroll"><view class="tabs"><button v-for="filter in filters" :key="filter.value" class="tab" :class="{ active: selected === filter.value }" @click="selectStatus(filter.value)">{{ filter.label }}</button></view></scroll-view>
      <view v-if="loading && !orders.length" class="ink-loading"><view class="ink-loading-mark" /><text>翻阅清单中…</text></view>
      <view v-else-if="error" class="ink-empty ink-card"><text class="ink-empty-title">清单暂时未能加载</text><text class="ink-empty-sub">{{ error }}</text><button class="ink-btn-secondary state-action" @click="load(page === 1)">重新加载</button></view>
      <view v-else-if="!orders.length" class="ink-empty ink-card"><text class="ink-empty-title">{{ selected !== 'ALL' ? '暂无此状态的订单' : business === 'COFFEE' ? '暂时还没有点单订单' : '还没有养物清单' }}</text><text class="ink-empty-sub">{{ business === 'COFFEE' ? '选一杯喜欢的饮品，到店自取' : '可以查看全部订单，或继续挑选养物' }}</text><button v-if="selected !== 'ALL'" class="ink-btn-secondary state-action" @click="selectStatus('ALL')">查看全部订单</button><button v-else class="ink-btn-primary state-action" @click="goBrowse">{{ business === 'COFFEE' ? '去选饮品' : '去逛逛' }}</button></view>
      <view v-else class="list">
        <view v-for="order in orders" :key="`${order.businessType}:${order.id}`" class="order-card ink-card" @click="goDetail(order)">
          <view class="row"><text class="store-name">{{ order.storeName }} · {{ order.businessType === 'GOODS' ? '商品配送' : '到店自取' }}</text><text class="order-status">{{ businessOrderStatusText[order.status] }}</text></view>
          <text class="order-no">{{ order.orderNo }}</text>
          <view v-for="(item, index) in order.summaryItems" :key="`${item.skuId}:${index}`" class="order-item"><image v-if="imageUrl(item.imageUrl)" class="item-cover" :src="imageUrl(item.imageUrl)" mode="aspectFill" /><view v-else class="item-cover placeholder">{{ order.businessType === 'GOODS' ? '养' : '饮' }}</view><view class="item-copy"><text class="item-name">{{ item.productName }}</text><text class="item-spec">{{ item.specName }} × {{ item.quantity }}</text></view><text class="ink-price">¥{{ cents(item.lineTotalCent) }}</text></view>
          <view class="order-bottom"><text>共 {{ order.itemCount }} {{ order.businessType === 'GOODS' ? '件' : '杯' }}</text><text class="amount">{{ order.amounts.paidAmountCent === null ? '应付' : '实付' }} <text class="ink-price">¥{{ cents(order.amounts.paidAmountCent ?? order.amounts.payableAmountCent) }}</text></text></view>
          <text class="detail-hint">查看详情{{ order.status === 'PENDING_PAY' ? ' · 继续付款' : order.status === 'PENDING_RECEIVE' ? ' · 确认收货' : '' }} →</text>
        </view>
        <button v-if="!finished" class="ink-btn-secondary load-more" :disabled="loading" @click="load(false)">{{ loading ? '加载中…' : '加载更多' }}</button><text v-else class="end-note">已展示全部订单</text>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onShow, onReachBottom, onHide } from '@dcloudio/uni-app'
import BusinessTabs from '@/components/BusinessTabs.vue'
import { listBusinessOrders, type BusinessOrderSummary } from '@/api/businessOrder'
import { businessOrderStatusText, statusGroupText, goodsStatuses, coffeeStatuses, type BusinessOrderStatus, type StatusGroup } from '@/constants/businessOrderStatus'
import { requireLogin } from '@/utils/auth'
import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/storage'
import { cents, imageUrl } from '@/utils/coffee'
import type { BusinessType } from '@/stores/businessContext'
const business = ref<'ALL' | BusinessType>('ALL'), selected = ref('ALL'), orders = ref<BusinessOrderSummary[]>([])
const loading = ref(false), error = ref(''), page = ref(1), finished = ref(false)
let requestId = 0
const auth = useAuthStore()
watch(() => auth.token, () => { ++requestId; orders.value = []; error.value = ''; loading.value = false; page.value = 1; finished.value = false })
const businessTabs = [{ value: 'ALL', label: '全部' }, { value: 'GOODS', label: '商品订单' }, { value: 'COFFEE', label: '点单订单' }]
const filters = computed(() => business.value === 'ALL' ? Object.entries(statusGroupText).map(([value, label]) => ({ value, label })) : [{ value: 'ALL', label: '全部' }, ...(business.value === 'GOODS' ? goodsStatuses : coffeeStatuses).map(value => ({ value, label: businessOrderStatusText[value] }))])
async function load(reset = true) {
  if (!requireLogin('/pages/orders/index')) { orders.value = []; return }
  if (!reset && (loading.value || finished.value)) return
  const currentRequest = ++requestId, token = getToken()
  if (reset) { page.value = 1; finished.value = false; orders.value = [] }
  loading.value = true; error.value = ''
  try {
    const data = await listBusinessOrders({ page: page.value, size: 20, businessType: business.value === 'ALL' ? undefined : business.value,
      statusGroup: business.value === 'ALL' ? selected.value as StatusGroup : undefined,
      status: business.value !== 'ALL' && selected.value !== 'ALL' ? selected.value as BusinessOrderStatus : undefined })
    if (currentRequest !== requestId || getToken() !== token) return
    orders.value = reset ? data.records : [...orders.value, ...data.records]
    finished.value = data.current >= data.pages || orders.value.length >= data.total
    page.value = data.current + 1
  } catch (e: any) { if (currentRequest === requestId && getToken() === token) error.value = e?.message || '网络暂时不通，请稍后重试' }
  finally { if (currentRequest === requestId) { loading.value = false; if (getToken() !== token) orders.value = [] } }
}
function selectBusiness(value: string) { business.value = value as 'ALL' | BusinessType; selected.value = 'ALL'; load() }
function selectStatus(value: string) { selected.value = value; load() }
function goBrowse() { uni.navigateTo({ url: business.value === 'COFFEE' ? '/pages/coffee/index' : '/pages/goods/index' }) }
function goDetail(order: BusinessOrderSummary) { uni.navigateTo({ url: `/pages/${order.businessType === 'GOODS' ? 'order-detail' : 'coffee-order'}/index?id=${encodeURIComponent(order.id)}` }) }
onShow(() => load())
onHide(() => { ++requestId; loading.value = false; if (!getToken()) orders.value = [] })
onReachBottom(() => load(false))
</script>
<style scoped>
.page-head { margin-bottom: 24rpx; }
.title { display: block; font-size: 40rpx; }
.page-description { display: block; margin-top: 12rpx; color: #65755d; font-size: 23rpx; }
.status-scroll { margin-bottom: 26rpx; }
.tabs { display: flex; gap: 12rpx; }
.tab { flex: none; padding: 12rpx 22rpx; line-height: 1.5; border-radius: 14rpx; color: #65755d; background: transparent; font-size: 25rpx; }
.tab.active { color: #435b34; background: #e8efdf; }
.list { display: flex; flex-direction: column; gap: 24rpx; }
.order-card { padding: 25rpx; animation: order-in 210ms ease-out; }
.row { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.store-name { font-size: 26rpx; flex: 1; }
.order-status { font-size: 25rpx; color: #637f51; }
.order-no { display: block; margin: 12rpx 0 20rpx; font-size: 21rpx; color: #65755d; overflow-wrap: anywhere; }
.order-item { display: flex; gap: 18rpx; align-items: center; margin-bottom: 18rpx; font-size: 25rpx; }
.item-cover { width: 98rpx; height: 110rpx; flex: none; border-radius: 12rpx; background: #edf2e6; }
.placeholder { display: flex; align-items: center; justify-content: center; color: #637f51; font-size: 30rpx; }
.item-copy { flex: 1; min-width: 0; }
.item-name, .item-spec { display: block; }
.item-spec { margin-top: 8rpx; font-size: 22rpx; color: #65755d; }
.order-bottom { border-top: 1rpx solid #e5ebdf; padding-top: 20rpx; display: flex; justify-content: space-between; align-items: center; font-size: 23rpx; color: #65755d; }
.amount .ink-price { font-size: 34rpx; }
.detail-hint { display: block; text-align: right; color: #637f51; font-size: 23rpx; margin-top: 18rpx; }
.state-action { margin-top: 24rpx; }
.end-note { text-align: center; font-size: 23rpx; color: #65755d; }
@keyframes order-in { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: none; } }
</style>
