<template>
  <view class="page">
    <view class="ink-page-content">
      <view class="page-head">
        <text class="kicker">ORDERS</text>
        <text class="title ink-title">养物清单</text>
      </view>

      <view class="tabs">
        <view class="tab" :class="{ active: status === undefined }" @click="selectStatus(undefined)">全部</view>
        <view class="tab" :class="{ active: status === 0 }" @click="selectStatus(0)">待支付</view>
        <view class="tab" :class="{ active: status === 10 }" @click="selectStatus(10)">待发货</view>
        <view class="tab" :class="{ active: status === 20 }" @click="selectStatus(20)">待收货</view>
        <view class="tab" :class="{ active: status === 30 }" @click="selectStatus(30)">已完成</view>
        <view class="tab" :class="{ active: status === 40 }" @click="selectStatus(40)">已取消</view>
      </view>

      <view v-if="loading" class="ink-loading">
        <view class="ink-loading-mark"></view>
        <text>翻阅清单中...</text>
      </view>
      <view v-else-if="error" class="ink-empty ink-card">
        <text class="ink-empty-title">清单暂时未能加载</text>
        <text class="ink-empty-sub">{{ error }}</text>
        <button class="ink-btn-secondary state-action" @click="load">重新加载</button>
      </view>
      <view v-else-if="orders.length === 0" class="ink-empty ink-card">
        <view class="ink-empty-art"></view>
        <text class="ink-empty-title">{{ status === undefined ? '还没有养物清单' : '暂无此状态的订单' }}</text>
        <text class="ink-empty-sub">{{ status === undefined ? '寻一味当季好物，开启顺时滋养' : '可以查看全部订单，或继续挑选养物' }}</text>
        <button v-if="status !== undefined" class="ink-btn-secondary state-action" @click="selectStatus(undefined)">查看全部订单</button>
        <button v-else class="ink-btn-primary state-action" @click="goHome">去逛逛</button>
      </view>

      <view v-else class="list">
        <view v-for="order in orders" :key="order.id" class="order-card ink-card" @click="goDetail(order.id)">
          <view class="row">
            <text class="order-no">{{ order.orderNo }}</text>
            <text class="ink-tag">{{ statusText(order.status) }}</text>
          </view>
          <view class="ink-line order-line"></view>
          <view v-for="item in order.items || []" :key="item.skuId" class="item">
            <text class="item-name">{{ item.productName }}</text>
            <text class="item-qty">x{{ item.quantity }}</text>
          </view>
          <view class="amount">{{ order.status === 0 ? '应付' : '订单金额' }} <text class="ink-price">¥{{ money(order.payAmount ?? order.totalAmount) }}</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { listOrders, type OrderVO } from '@/api/order'
import { money, requireLogin } from '@/utils/auth'
import { statusText } from '@/constants/orderStatus'

const orders = ref<OrderVO[]>([])
const loading = ref(false)
const error = ref('')
let requestId = 0
const status = ref<number | undefined>(undefined)

async function load() {
  if (!requireLogin('/pages/orders/index')) return
  const currentRequest = ++requestId
  loading.value = true
  error.value = ''
  try {
    const data = await listOrders({ page: 1, size: 20, status: status.value })
    if (currentRequest === requestId) orders.value = data.records || []
  } catch (e: any) {
    if (currentRequest === requestId) error.value = e?.message || '网络暂时不通，请稍后重试'
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

function selectStatus(next?: number) {
  status.value = next
  load()
}

function goHome() { uni.switchTab({ url: '/pages/home/index' }) }

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/order-detail/index?id=${id}` })
}

onShow(load)
</script>

<style scoped>
.state-action { margin-top: 26rpx; min-width: 240rpx; }
.page-head {
  margin-bottom: 24rpx;
}
.kicker {
  display: block;
  color: #7f9f52;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 20rpx;
  margin-bottom: 6rpx;
}
.title {
  display: block;
  font-size: 40rpx;
}
.tabs {
  display: flex;
  gap: 12rpx;
  overflow-x: auto;
  margin-bottom: 28rpx;
}
.tab {
  flex: 0 0 auto;
  padding: 12rpx 24rpx;
  border: 1rpx solid rgba(111, 159, 88, 0.24);
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.82);
  color: #2d2d2d;
  font-family: "Songti SC", "STSong", serif;
  font-size: 26rpx;
  transition: all 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.tab.active {
  border-color: transparent;
  background: linear-gradient(135deg, #5f8f4b, #86c166 72%, #cfe2c8);
  color: #fff;
}
.hint {
  color: #6f7b68;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.order-card {
  padding: 26rpx;
  animation: order-in 0.4s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}
.order-card:nth-child(1) { animation-delay: 0.03s; }
.order-card:nth-child(2) { animation-delay: 0.08s; }
.order-card:nth-child(3) { animation-delay: 0.13s; }
.order-card:nth-child(4) { animation-delay: 0.18s; }

@keyframes order-in {
  from { opacity: 0; transform: translateY(14rpx); }
  to   { opacity: 1; transform: translateY(0); }
}
.row,
.item {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
}
.order-no {
  font-size: 26rpx;
  color: #2d2d2d;
  font-family: Georgia, "Times New Roman", serif;
}
.order-line {
  position: relative;
  z-index: 1;
  margin: 18rpx 0;
}
.item {
  margin-top: 14rpx;
  color: #6f7b68;
  font-size: 26rpx;
}
.item-name {
  max-width: 520rpx;
}
.amount {
  position: relative;
  z-index: 1;
  margin-top: 18rpx;
  text-align: right;
  color: #6f7b68;
  font-size: 30rpx;
}
</style>
