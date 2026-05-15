<template>
  <view class="page">
    <view class="ink-page-content">
      <view v-if="loading" class="ink-loading">
        <view class="ink-loading-mark"></view>
        <text>查阅订单中...</text>
      </view>
      <view v-else-if="!order" class="hint">订单不存在</view>
      <view v-else>
        <view class="card ink-card">
          <view class="row">
            <text class="label">订单号</text>
            <text class="order-no">{{ order.orderNo }}</text>
          </view>
          <view class="ink-line"></view>
          <view class="row">
            <text class="label">状态</text>
            <text class="ink-tag">{{ statusText(order.status) }}</text>
          </view>
          <view class="ink-line"></view>
          <view class="row">
            <text class="label">实付</text>
            <text class="amount ink-price">¥{{ money(order.payAmount || order.totalAmount) }}</text>
          </view>
        </view>

        <view class="card ink-card">
          <view class="title ink-title">商品</view>
          <view v-for="item in order.items || []" :key="item.skuId" class="item">
            <text>{{ item.productName }}</text>
            <text>x{{ item.quantity }}</text>
          </view>
        </view>

        <view class="card ink-card">
          <view class="title ink-title">收货信息</view>
          <view class="text">{{ order.receiverName }} {{ order.receiverPhone }}</view>
          <view class="text">{{ order.receiverAddress }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getOrder, type OrderVO } from '@/api/order'
import { money } from '@/utils/auth'

const id = ref<number>(0)
const order = ref<OrderVO | null>(null)
const loading = ref(false)

async function load() {
  if (!id.value) return
  loading.value = true
  try {
    order.value = await getOrder(id.value)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function statusText(value?: number) {
  const map: Record<number, string> = {
    0: '待支付',
    1: '已支付',
    2: '待发货',
    3: '已完成',
    4: '已关闭',
  }
  return value === undefined ? '-' : map[value] || `状态${value}`
}

onLoad((options) => {
  id.value = Number((options as any)?.id || 0)
  load()
})
</script>

<style scoped>
.hint {
  color: #6f7b68;
  margin-bottom: 12rpx;
}
.card {
  padding: 28rpx;
  margin-bottom: 24rpx;
}
.row,
.item {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  padding: 18rpx 0;
}
.label,
.text {
  color: #6f7b68;
}
.order-no {
  color: #2d2d2d;
  font-family: Georgia, "Times New Roman", serif;
}
.amount {
  font-size: 32rpx;
}
.title {
  position: relative;
  z-index: 1;
  font-size: 30rpx;
  margin-bottom: 14rpx;
}
</style>
