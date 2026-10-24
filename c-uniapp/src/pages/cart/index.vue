<template>
  <view class="page">
    <view class="ink-page-content">
      <view class="header"><view><text class="kicker">HERBAL BASKET</text><text class="title ink-title">一篮养物</text></view><button class="refresh-control" :disabled="cart.busy || cart.loading" @click="cart.refresh()">刷新</button></view>
      <CartItems />
    </view>
    <view v-if="cart.items.length && !cart.error && !cart.loading" class="settle-bar">
      <button class="select-all" :disabled="cart.busy" @click="cart.setAllChecked(cart.allChecked ? 0 : 1)">{{ cart.allChecked ? '✓ 全选' : '○ 全选' }}</button>
      <view class="settle-total"><text class="sum-label">{{ cart.busy ? '正在更新…' : `已选 ${cart.checkedCount} 件` }}</text><text class="sum ink-price">¥{{ cart.checkedAmountText }}</text></view>
      <button class="ink-btn-primary settle-btn" :disabled="!!cart.checkoutIssue" @click="goCreateOrder">去结算</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import CartItems from '@/components/CartItems.vue'
import { requireLogin } from '@/utils/auth'
import { useCartStore } from '@/stores/cart'
const cart = useCartStore()
function goCreateOrder() {
  if (cart.prepareCheckout('/pages/cart/index')) uni.navigateTo({ url: '/pages/order-create/index' })
}
onShow(() => {
  if (requireLogin('/pages/cart/index')) cart.refresh()
})
</script>

<style scoped>
.page { padding-bottom: calc(200rpx + env(safe-area-inset-bottom)); }
.header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 28rpx; }
.kicker { display: block; margin-bottom: 6rpx; color: #7f9f52; font-family: Georgia, serif; font-size: 20rpx; }
.title { display: block; font-size: 40rpx; }
.refresh-control, .select-all { padding: 8rpx 0; margin: 0; background: transparent; font-size: 26rpx; line-height: 1.5; color: #638950; flex-shrink: 0; }
.refresh-control::after, .select-all::after { border: 0; }
.settle-bar { position: fixed; z-index: 50; left: 0; right: 0; bottom: var(--window-bottom, 0px); display: flex; align-items: center; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); background: #fcfdf9; border-top: 1rpx solid #dde7d6; box-shadow: 0 -8rpx 28rpx rgba(57,83,43,.06); }
.settle-total { flex: 1; min-width: 0; padding: 0 16rpx; }
.sum-label { display: block; color: #6f7b68; font-size: 23rpx; }
.sum { display: block; font-size: 34rpx; margin-top: 4rpx; }
.settle-btn { width: 200rpx; margin: 0; }
button[disabled] { opacity: .45; }
</style>
