<template><view class="glass-action-bar" :class="{ 'above-business-nav': businessNav }"><slot /></view></template>
<script setup lang="ts">defineProps<{ businessNav?: boolean }>()</script>
<style scoped>
.glass-action-bar { position: fixed; left: 23rpx; right: 23rpx; bottom: calc(23rpx + var(--window-bottom, 0px)); z-index: 50; min-height: 130rpx; padding: 18rpx 23rpx; border-radius: 42rpx; display: flex; align-items: center; gap: 18rpx; background: #edf3e7; border: 1rpx solid #fffef9; box-shadow: 0 12rpx 30rpx rgba(66, 86, 51, .13); }
.above-business-nav { bottom: calc(123rpx + env(safe-area-inset-bottom)); }
@supports ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .glass-action-bar { background: linear-gradient(120deg, rgba(235,244,226,.86), rgba(220,234,206,.78)); backdrop-filter: blur(22px) saturate(1.35); -webkit-backdrop-filter: blur(22px) saturate(1.35); }
}
</style>
