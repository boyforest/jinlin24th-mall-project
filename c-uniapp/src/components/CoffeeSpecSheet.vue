<template>
  <view v-if="displayProduct" class="spec-mask" @click="$emit('close')">
    <view class="spec-sheet" :class="{ closing }" role="dialog" aria-modal="true" :aria-label="`${displayProduct.name}规格`" @click.stop>
      <view class="sheet-head"><text class="sheet-title">{{ displayProduct.name }}</text><button aria-label="关闭规格" @click="$emit('close')">×</button></view>
      <text class="sheet-description">{{ displayProduct.description }}</text>
      <text class="option-label">温度</text>
      <view class="options"><button v-for="temperature in temperatures" :key="temperature" :class="{ selected: selected?.temperature === temperature }" @click="selectTemperature(temperature)">{{ temperatureText[temperature] }}</button></view>
      <text class="option-label">甜度</text>
      <view class="options"><button v-for="sku in sugarSkus" :key="sku.id" :class="{ selected: selected?.id === sku.id }" :disabled="!sku.available" @click="$emit('select', sku.id)">{{ sugarText[sku.sugar] }}{{ sku.available ? '' : ' · 售罄' }}</button></view>
      <text v-if="!selected?.available" class="sheet-description">此规格暂时售罄</text>
      <view class="sheet-foot"><text class="ink-price">{{ selected ? `¥${cents(selected.priceCent)}` : '暂无可售规格' }}</text><button class="ink-btn-primary" :disabled="busy || !selected?.available || !canAdd" @click="$emit('add')">{{ busy ? '正在加入…' : canAdd ? '加入咖啡篮' : '暂不可加购' }}</button></view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { CoffeeProduct, Temperature } from '@/api/coffee'
import { cents, temperatureText, sugarText } from '@/utils/coffee'
const props = defineProps<{ product: CoffeeProduct | null; selectedId: string; busy: boolean; canAdd: boolean }>()
const displayProduct = ref(props.product)
const closing = ref(false)
let closeTimer: ReturnType<typeof setTimeout> | undefined
watch(() => props.product, product => {
  if (closeTimer) clearTimeout(closeTimer)
  if (product) { displayProduct.value = product; closing.value = false }
  else if (displayProduct.value) {
    closing.value = true
    closeTimer = setTimeout(() => { displayProduct.value = null; closing.value = false }, 160)
  }
})
onBeforeUnmount(() => { if (closeTimer) clearTimeout(closeTimer) })
const emit = defineEmits<{ (e: 'close'): void; (e: 'select', id: string): void; (e: 'add'): void }>()
const selected = computed(() => displayProduct.value?.skus.find(sku => sku.id === props.selectedId))
const temperatures = computed(() => [...new Set(displayProduct.value?.skus.map(sku => sku.temperature) || [])])
const sugarSkus = computed(() => displayProduct.value?.skus.filter(sku => sku.temperature === selected.value?.temperature) || [])
function selectTemperature(temperature: Temperature) {
  const matches = displayProduct.value?.skus.filter(sku => sku.temperature === temperature) || []
  const next = matches.find(sku => sku.sugar === selected.value?.sugar && sku.available) || matches.find(sku => sku.available) || matches[0]
  if (next) emit('select', next.id)
}
</script>
<style scoped>
.spec-mask { position: fixed; inset: 0; z-index: 100; display: flex; align-items: flex-end; background: rgba(29,40,24,.3); }
.spec-sheet { width: 100%; padding: 36rpx 36rpx calc(40rpx + env(safe-area-inset-bottom)); border-radius: 40rpx 40rpx 0 0; background: #f7f9f2; border: 1rpx solid #fff; animation: sheet-in 260ms ease-out; }
.sheet-head, .sheet-foot { display: flex; align-items: center; justify-content: space-between; gap: 24rpx; }
.sheet-title { font-size: 36rpx; font-weight: 600; }
.sheet-head button { margin: 0; background: transparent; font-size: 40rpx; }
.sheet-head button::after { border: 0; }
.sheet-description { display: block; margin: 14rpx 0; color: #65755d; font-size: 25rpx; }
.option-label { display: block; margin: 24rpx 0 14rpx; font-size: 26rpx; }
.options { display: flex; gap: 16rpx; flex-wrap: wrap; }
.options button { margin: 0; padding: 14rpx 28rpx; font-size: 26rpx; line-height: 1.4; color: #65755d; background: #fffef9; border: 1rpx solid #dce5d4; transition: background 220ms, border-color 220ms; }
.options button.selected { color: #435b34; background: #e2ecd9; border-color: #637f51; }
.sheet-foot { margin-top: 38rpx; }
.sheet-foot .ink-price { font-size: 40rpx; }
.sheet-foot button { min-width: 250rpx; }
button[disabled] { opacity: .45; }
.spec-sheet.closing { animation: sheet-out 160ms ease-in forwards; pointer-events: none; }
@keyframes sheet-out { from { opacity: 1; transform: translateY(0); } to { opacity: 0; transform: translateY(8px); } }
@keyframes sheet-in { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
</style>
