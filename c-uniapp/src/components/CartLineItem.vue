<template>
      <view class="cart-row ink-card" :class="{ pending, compact }">
        <button class="check" :class="{ checked: checked }" :disabled="busy || checkDisabled" :aria-label="`${checked ? '取消选择' : '选择'}${name}`" @click="$emit('check', checked ? 0 : 1)">{{ checked ? '✓' : '' }}</button>
        <image v-if="image" class="cover" :src="image" mode="aspectFill" />
        <view v-else class="cover placeholder">养</view>
        <view class="meta">
          <text class="name">{{ name }}</text>
          <text class="spec">{{ spec }}</text>
          <text v-if="!!warning" class="stock-warning">{{ warning }}</text>
          <view class="price-row">
            <text class="price ink-price">¥{{ price }}</text>
            <view class="stepper">
              <button class="step" :disabled="busy || quantity <= 1" aria-label="减少数量" @click="$emit('quantity', quantity - 1)">−</button>
              <text class="quantity">{{ quantity }}</text>
              <button class="step" :disabled="busy || !canIncrease" aria-label="增加数量" @click="$emit('quantity', quantity + 1)">+</button>
            </view>
          </view>
          <view class="item-bottom"><text class="feedback">{{ pending ? '正在更新…' : '' }}</text><button class="remove" :disabled="busy" @click="$emit('remove')">移出</button></view>
        </view>
      </view>
</template>
<script setup lang="ts">
defineProps<{ name: string; spec: string; image?: string; price: string; quantity: number; checked: boolean; busy: boolean; pending?: boolean; compact?: boolean; checkDisabled?: boolean; canIncrease: boolean; warning?: string }>()
defineEmits<{ (e: 'check', value: number): void; (e: 'quantity', value: number): void; (e: 'remove'): void }>()
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
.cart-row.compact { padding: 18rpx 0; background: transparent; border-radius: 0; box-shadow: none; border: 0; border-bottom: 1rpx solid #e5eddc; }
.cart-row.compact::before, .cart-row.compact::after { display: none; }
.cart-row.compact .cover { width: 96rpx; height: 112rpx; }
.cart-row.compact .check { margin-top: 30rpx; }
</style>
