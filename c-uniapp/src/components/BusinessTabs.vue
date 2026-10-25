<template>
  <view class="business-tabs" role="tablist">
    <button v-for="tab in tabs" :key="tab.value" class="business-tab" :class="{ active: modelValue === tab.value }" role="tab" :aria-selected="modelValue === tab.value" @click="$emit('update:modelValue', tab.value)">
      {{ tab.label }}<text v-if="tab.count !== undefined" class="tab-count">{{ tab.count }}</text>
    </button>
  </view>
</template>
<script setup lang="ts">
defineProps<{ modelValue: string; tabs: { value: string; label: string; count?: number }[] }>()
defineEmits<{ (e: 'update:modelValue', value: string): void }>()
</script>
<style scoped>
.business-tabs { display: flex; gap: 32rpx; margin-bottom: 28rpx; overflow-x: auto; }
.business-tab { position: relative; flex: none; padding: 12rpx 0 24rpx; background: transparent; color: #65755d; font-size: 29rpx; line-height: 1.5; transition: color 240ms; }
.business-tab::after { border: 0; }
.business-tab.active { color: #2e382c; font-weight: 600; }
.business-tab.active::before { content: ''; position: absolute; width: 36rpx; height: 5rpx; bottom: 4rpx; left: calc(50% - 18rpx); border-radius: 4rpx; background: #637f51; }
.tab-count { margin-left: 10rpx; font-size: 23rpx; color: #65755d; }
</style>
