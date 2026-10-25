<template>
  <view class="business-nav">
    <button v-for="tab in tabs" :key="tab.path" @click="go(tab.path)"><image :src="`/static/tabbar/${tab.icon}.png`" /><text>{{ tab.label }}</text></button>
  </view>
</template>
<script setup lang="ts">
import { useBusinessContext, type BusinessType } from '@/stores/businessContext'
const props = defineProps<{ business: BusinessType }>()
const context = useBusinessContext()
const tabs = [{ path: 'home', icon: 'home', label: '首页' }, { path: 'cart', icon: 'cart', label: '购物车' }, { path: 'orders', icon: 'orders', label: '订单' }, { path: 'my', icon: 'my', label: '我的' }]
function go(path: string) {
  if (path === 'cart') context.cartBusiness = props.business
  uni.switchTab({ url: `/pages/${path}/index` })
}
</script>
<style scoped>
.business-nav { position: fixed; z-index: 60; bottom: 0; left: 0; right: 0; display: flex; height: calc(100rpx + env(safe-area-inset-bottom)); padding-bottom: env(safe-area-inset-bottom); background: #fffef9; border-top: 1px solid #edf0e7; }
.business-nav button { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; background: transparent; line-height: 1.2; color: #7b8378; font-size: 10px; }
.business-nav button::after { border: 0; }
.business-nav image { width: 24px; height: 24px; margin-bottom: 3px; }
</style>
