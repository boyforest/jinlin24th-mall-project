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
      <view v-else-if="orders.length === 0" class="ink-empty ink-card">
        <view class="ink-empty-art"></view>
        <text class="ink-empty-title">暂无内容</text>
        <text class="ink-empty-sub">还没有养物清单</text>
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
          <view class="amount">实付 <text class="ink-price">¥{{ money(order.payAmount || order.totalAmount) }}</text></view>
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
const status = ref<number | undefined>(undefined)

async function load() {
  if (!requireLogin('/pages/orders/index')) return
  loading.value = true
  try {
    const data = await listOrders({ page: 1, size: 20, status: status.value })
    orders.value = data.records || []
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function selectStatus(next?: number) {
  status.value = next
  load()
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/order-detail/index?id=${id}` })
}

onShow(load)
</script>

<style scoped>
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
