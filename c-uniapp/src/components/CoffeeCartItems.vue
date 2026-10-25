<template>
  <view class="coffee-cart-items" :class="{ compact }">
    <view v-if="!authenticated" class="ink-empty"><text class="ink-empty-title">登录后查看咖啡篮</text><button class="ink-btn-primary state-action" @click="login">去登录</button></view>
    <view v-else-if="cart.loading" class="ink-loading"><view class="ink-loading-mark" /><text>正在整理饮品…</text></view>
    <view v-else-if="cart.error" class="ink-empty"><text class="ink-empty-title">咖啡篮暂时未能同步</text><text class="ink-empty-sub">{{ cart.error }}</text><button class="ink-btn-secondary state-action" @click="cart.refresh()">重新加载</button></view>
    <view v-else-if="!cart.items.length" class="ink-empty"><text class="ink-empty-title">咖啡篮还空着</text><text class="ink-empty-sub">点单筹备中，可先浏览与加购</text><button class="ink-btn-primary state-action" @click="browse">去选饮品</button></view>
    <view v-else class="cart-list">
      <CartLineItem :compact="compact" v-for="item in cart.items" :key="item.id" :name="item.productName" :spec="`${item.skuName} · ${skuDescription(item)}`"
        :image="imageUrl(item.imageUrl)" :price="cents(item.unitPriceCent)" :quantity="item.quantity" :checked="item.checked === 1"
        :check-disabled="!item.available && item.checked === 0" :busy="cart.busy" :pending="cart.pending === item.id"
        :can-increase="item.available && item.quantity < 99" :warning="item.available ? '' : unavailableText(item.unavailableReason)"
        @check="cart.setChecked(item, $event)" @quantity="cart.setQuantity(item, $event)" @remove="cart.remove(item.id)" />
    </view>
  </view>
</template>
<script setup lang="ts">
import CartLineItem from './CartLineItem.vue'
import { useCoffeeCartStore } from '@/stores/coffeeCart'
import { requireLogin } from '@/utils/auth'
import { cents, imageUrl, skuDescription, unavailableText } from '@/utils/coffee'
defineProps<{ compact?: boolean; authenticated: boolean }>()
const emit = defineEmits<{ (e: 'browse'): void }>()
const cart = useCoffeeCartStore()
function login() { requireLogin('/pages/cart/index') }
function browse() { emit('browse'); uni.navigateTo({ url: '/pages/coffee/index' }) }
</script>
<style scoped>
.cart-list { display: flex; flex-direction: column; gap: 22rpx; }
.state-action { margin-top: 26rpx; }
</style>
