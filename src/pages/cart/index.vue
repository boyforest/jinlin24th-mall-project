<template>
  <view class="page">
    <view class="ink-page-content">
      <view class="header">
        <view>
          <text class="kicker">HERBAL BASKET</text>
          <text class="title ink-title">一篮养物</text>
        </view>
        <text class="refresh" @click="load">刷新</text>
      </view>

      <view v-if="loading" class="ink-loading">
        <view class="ink-loading-mark"></view>
        <text>整理养物中...</text>
      </view>
      <view v-else-if="items.length === 0" class="ink-empty ink-card">
        <view class="ink-empty-art"></view>
        <text class="ink-empty-title">暂无内容</text>
        <text class="ink-empty-sub">慢寻一味，顺时而养</text>
        <button class="ink-btn-primary empty-btn" @click="goHome">去逛逛</button>
      </view>

      <view v-else class="list">
        <view v-for="item in items" :key="item.id" class="cart-card ink-card">
          <image v-if="item.productMainImage" class="cover" :src="item.productMainImage" mode="aspectFill" />
          <view v-else class="cover placeholder">JL</view>
          <view class="meta">
            <text class="name ink-title">{{ item.productName }}</text>
            <text class="sku">{{ item.skuName }}</text>
            <view class="bottom">
              <text class="price ink-price">¥{{ money(item.memberPrice || item.price) }}</text>
              <view class="stepper">
                <text class="step" @click="changeQty(item, -1)">-</text>
                <text class="qty">{{ item.quantity }}</text>
                <text class="step" @click="changeQty(item, 1)">+</text>
              </view>
            </view>
            <text class="delete" @click="remove(item.id)">移出</text>
          </view>
        </view>
      </view>

      <view v-if="items.length" class="settle-bar">
        <view>
          <text class="sum-label">合计</text>
          <text class="sum ink-price">¥{{ money(totalAmount) }}</text>
        </view>
        <button class="ink-btn-primary settle-btn" @click="goCreateOrder">去结算</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { deleteCart, listCart, updateCart, type CartVO } from '@/api/cart'
import { money, requireLogin } from '@/utils/auth'

const items = ref<CartVO[]>([])
const loading = ref(false)

const totalAmount = computed(() =>
  items.value.reduce((sum, item) => sum + Number(item.memberPrice || item.price || 0) * item.quantity, 0),
)

async function load() {
  if (!requireLogin('/pages/cart/index')) return
  loading.value = true
  try {
    items.value = await listCart()
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function changeQty(item: CartVO, delta: number) {
  const next = Math.max(1, item.quantity + delta)
  if (next === item.quantity) return
  try {
    await updateCart(item.id, { quantity: next, checked: item.checked })
    item.quantity = next
  } catch (e: any) {
    uni.showToast({ title: e?.message || '更新失败', icon: 'none' })
  }
}

async function remove(id: number) {
  try {
    await deleteCart(id)
    items.value = items.value.filter((item) => item.id !== id)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '删除失败', icon: 'none' })
  }
}

function goCreateOrder() {
  if (!items.value.length) return
  const skuId = items.value[0].skuId
  uni.navigateTo({ url: `/pages/order-create/index?skuId=${skuId}` })
}

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}

onShow(load)
</script>

<style scoped>
.page {
  padding-bottom: 120rpx;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28rpx;
}
.kicker {
  display: block;
  margin-bottom: 6rpx;
  color: #7f9f52;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 20rpx;
}
.title {
  display: block;
  font-size: 40rpx;
}
.refresh,
.delete {
  color: #5f8f4b;
  font-size: 26rpx;
}
.hint {
  color: #6f7b68;
}
.empty-btn {
  margin-top: 24rpx;
  width: 240rpx;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.cart-card {
  display: flex;
  padding: 18rpx;
}
.cover {
  width: 156rpx;
  height: 156rpx;
  border-radius: 18rpx;
  background: #eef6eb;
}
.cover.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5f8f4b;
  font-family: "Songti SC", "STSong", serif;
  font-weight: 700;
}
.meta {
  flex: 1;
  position: relative;
  z-index: 1;
  padding-left: 18rpx;
}
.name,
.price {
  display: block;
  font-size: 30rpx;
}
.sku {
  display: block;
  color: #6f7b68;
  font-size: 24rpx;
  margin-top: 8rpx;
}
.bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14rpx;
}
.stepper {
  display: flex;
  align-items: center;
  border: 1rpx solid rgba(111, 159, 88, 0.3);
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.72);
}
.step,
.qty {
  min-width: 52rpx;
  text-align: center;
  line-height: 48rpx;
}
.settle-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18rpx 32rpx;
  background: rgba(253, 253, 251, 0.96);
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 -4rpx 16rpx rgba(134, 193, 102, 0.1);
}
.sum-label {
  color: #6f7b68;
  font-size: 24rpx;
  margin-right: 10rpx;
}
.sum {
  font-size: 34rpx;
}
.settle-btn {
  width: 220rpx;
}
</style>
