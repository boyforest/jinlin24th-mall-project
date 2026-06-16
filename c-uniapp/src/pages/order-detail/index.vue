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
          <view v-if="order.payTime" class="ink-line"></view>
          <view v-if="order.payTime" class="row">
            <text class="label">支付时间</text>
            <text class="time">{{ formatTime(order.payTime) }}</text>
          </view>
          <view v-if="order.deliveryTime" class="ink-line"></view>
          <view v-if="order.deliveryTime" class="row">
            <text class="label">发货时间</text>
            <text class="time">{{ formatTime(order.deliveryTime) }}</text>
          </view>
          <view v-if="order.receiveTime" class="ink-line"></view>
          <view v-if="order.receiveTime" class="row">
            <text class="label">完成时间</text>
            <text class="time">{{ formatTime(order.receiveTime) }}</text>
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

        <button v-if="order.status === 0" class="ink-btn-primary pay-btn" :loading="paying" @click="payNow">继续支付</button>
        <button v-if="order.status === 20" class="ink-btn-primary pay-btn" :loading="receiving" @click="confirmReceive">确认收货</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getOrder, receiveOrder, type OrderVO } from '@/api/order'
import { createOrderPayment, requestMiniAppPayment } from '@/api/payment'
import { money } from '@/utils/auth'
import { statusText } from '@/constants/orderStatus'

const id = ref<number>(0)
const order = ref<OrderVO | null>(null)
const loading = ref(false)
const paying = ref(false)
const receiving = ref(false)

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

async function payNow() {
  if (!order.value?.id) return
  paying.value = true
  try {
    const payParams = await createOrderPayment(order.value.id)
    await requestMiniAppPayment(payParams)
    uni.showToast({ title: '支付完成', icon: 'success' })
    await load()
  } catch (e: any) {
    const message = String(e?.errMsg || e?.message || '')
    const isCancel = message.includes('cancel')
    uni.showToast({ title: isCancel ? '支付已取消' : message || '支付未完成', icon: 'none' })
  } finally {
    paying.value = false
  }
}

function formatTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : ''
}

function confirmReceive() {
  if (!order.value?.id) return
  uni.showModal({
    title: '确认收货',
    content: '确认已经收到这份养物？',
    confirmText: '确认',
    success: async (res) => {
      if (!res.confirm || !order.value?.id) return
      receiving.value = true
      try {
        order.value = await receiveOrder(order.value.id)
        uni.showToast({ title: '订单已完成', icon: 'success' })
      } catch (e: any) {
        uni.showToast({ title: e?.message || '确认失败', icon: 'none' })
      } finally {
        receiving.value = false
      }
    },
  })
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
.time {
  color: #2d2d2d;
  font-size: 24rpx;
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
/* 状态标签过渡 */
:deep(.ink-tag) {
  transition: all 0.3s ease;
}

.pay-btn {
  margin-top: 12rpx;
}
</style>
