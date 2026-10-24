<template>
  <view class="cart-items" :class="{ compact }">
    <view v-if="cart.loading" class="ink-loading"><view class="ink-loading-mark"></view><text>正在整理养物…</text></view>
    <view v-else-if="cart.error" class="ink-empty state-card">
      <text class="ink-empty-title">购物车暂时未能同步</text>
      <text class="ink-empty-sub">{{ cart.error }}</text>
      <button class="ink-btn-secondary state-action" @click="cart.refresh()">重新加载</button>
    </view>
    <view v-else-if="!cart.items.length" class="ink-empty state-card">
      <view class="ink-empty-art"></view>
      <text class="ink-empty-title">篮中还没有养物</text>
      <text class="ink-empty-sub">寻一味当季好物，顺时而养</text>
      <button class="ink-btn-primary state-action" @click="browse">去逛逛</button>
    </view>
    <view v-else class="cart-list">
      <view v-for="item in cart.items" :key="item.id" class="cart-row ink-card" :class="{ pending: cart.pendingId === item.id }">
        <button class="check" :class="{ checked: item.checked !== 0 }" :disabled="cart.busy" :aria-label="`${item.checked !== 0 ? '取消选择' : '选择'}${item.productName || '商品'}`" @click="cart.setChecked(item, item.checked === 0 ? 1 : 0)">{{ item.checked !== 0 ? '✓' : '' }}</button>
        <image v-if="item.skuImage || item.productMainImage" class="cover" :src="item.skuImage || item.productMainImage" mode="aspectFill" />
        <view v-else class="cover placeholder">养</view>
        <view class="meta">
          <text class="name">{{ item.productName || '商品信息待更新' }}</text>
          <text class="spec">{{ item.skuName || '默认规格' }}</text>
          <text v-if="item.stock != null && item.stock < item.quantity" class="stock-warning">{{ item.stock ? `仅余 ${item.stock} 件，请调整数量` : '暂时售罄，可移出或取消勾选' }}</text>
          <view class="price-row">
            <text class="price ink-price">¥{{ money(item.effectivePrice ?? item.price) }}</text>
            <view class="stepper">
              <button class="step" :disabled="cart.busy || item.quantity <= 1" aria-label="减少数量" @click="cart.setQuantity(item, item.quantity - 1)">−</button>
              <text class="quantity">{{ item.quantity }}</text>
              <button class="step" :disabled="cart.busy || item.quantity >= (item.stock ?? 0)" aria-label="增加数量" @click="cart.setQuantity(item, item.quantity + 1)">+</button>
            </view>
          </view>
          <view class="item-bottom"><text class="feedback">{{ cart.pendingId === item.id ? '正在更新…' : '' }}</text><button class="remove" :disabled="cart.busy" @click="cart.remove(item.id)">移出</button></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { useCartStore } from '@/stores/cart'
import { money } from '@/utils/auth'
defineProps<{ compact?: boolean }>()
const emit = defineEmits<{ (event: 'browse'): void }>()
const cart = useCartStore()
function browse() {
  emit('browse')
  uni.switchTab({ url: '/pages/home/index' })
}
</script>

<style scoped>
.cart-list { display: flex; flex-direction: column; gap: 22rpx; }
.cart-row { display: flex; align-items: flex-start; gap: 16rpx; padding: 22rpx 18rpx; }
.pending { opacity: .7; }
.check { position: relative; z-index: 1; flex-shrink: 0; width: 42rpx; height: 42rpx; line-height: 38rpx; padding: 0; margin: 42rpx 0 0; border: 2rpx solid #9eb590; border-radius: 50%; background: #fff; color: transparent; font-size: 28rpx; }
.check.checked { background: #638f4f; border-color: #638f4f; color: #fff; }
button::after { border: none; }
.cover { flex-shrink: 0; width: 132rpx; height: 150rpx; border-radius: 14rpx; background: #edf4e7; }
.placeholder { display: flex; align-items: center; justify-content: center; color: #789768; font-size: 40rpx; }
.meta { position: relative; z-index: 1; flex: 1; min-width: 0; }
.name { display: block; font-size: 28rpx; line-height: 1.5; overflow-wrap: anywhere; color: #30482a; }
.spec { display: block; font-size: 23rpx; color: #6f7b68; margin-top: 6rpx; }
.price-row { display: flex; align-items: center; justify-content: space-between; gap: 8rpx; flex-wrap: wrap; margin-top: 16rpx; }
.price { font-size: 30rpx; }
.stepper { display: flex; align-items: center; border: 1rpx solid #dae5d1; border-radius: 10rpx; }
.step { width: 52rpx; height: 54rpx; line-height: 52rpx; padding: 0; margin: 0; background: transparent; color: #416233; font-size: 30rpx; }
.quantity { min-width: 40rpx; text-align: center; font-size: 26rpx; }
.item-bottom { display: flex; align-items: center; justify-content: space-between; margin-top: 12rpx; }
.feedback { color: #758968; font-size: 22rpx; }
.remove { padding: 6rpx 0 6rpx 20rpx; margin: 0; line-height: 1.5; font-size: 23rpx; color: #75856a; background: transparent; }
.stock-warning { display: block; color: #ad604d; font-size: 22rpx; margin-top: 8rpx; }
.state-card { padding: 40rpx 24rpx; }
.state-action { margin-top: 24rpx; min-width: 220rpx; }
button[disabled] { opacity: .4; }
.compact .cart-row { padding: 18rpx 0; background: transparent; border-radius: 0; box-shadow: none; border: 0; border-bottom: 1rpx solid #e5eddc; }
.compact .cart-row::before, .compact .cart-row::after { display: none; }
.compact .cover { width: 96rpx; height: 112rpx; }
.compact .check { margin-top: 30rpx; }
</style>
