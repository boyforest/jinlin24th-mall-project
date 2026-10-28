<template>
  <view class="page business-page">
    <view class="ink-page-content">
      <view class="header"><text class="title ink-title">我的购物车</text><button class="refresh-control" :disabled="activeCart.busy || activeCart.loading" @click="refresh">刷新</button></view>
      <BusinessTabs :model-value="context.cartBusiness" :tabs="tabs" @update:model-value="selectBusiness" />
      <text class="business-note">{{ isCoffee ? coffeeOrderingText(context.coffeeStore) : '商品配送 · 商品与咖啡分别结算' }}</text>
      <view v-if="!authenticated" class="ink-empty"><text class="ink-empty-title">登录后查看购物车</text><button class="ink-btn-primary state-action" @click="login">去登录</button></view>
      <template v-else-if="isCoffee">
        <view v-if="storeLoading" class="ink-loading">正在查看门店…</view>
        <view v-else-if="storeError" class="ink-empty"><text class="ink-empty-title">门店暂时无法加载</text><text class="ink-empty-sub">{{ storeError }}</text><button class="ink-btn-secondary state-action" @click="refresh">重新加载</button></view>
        <CoffeeCartItems v-else :authenticated="authenticated" />
      </template>
      <CartItems v-else />
    </view>
    <GlassActionBar v-if="authenticated && activeCart.items.length && !activeCart.error && !activeCart.loading && (!isCoffee || !storeError)" class="settle-bar">
      <button class="select-all" :disabled="activeCart.busy" @click="activeCart.setAllChecked(activeCart.allChecked ? 0 : 1)">{{ activeCart.allChecked ? '✓ 全选' : '○ 全选' }}</button>
      <view class="settle-total"><text class="sum-label">{{ activeCart.busy ? '正在更新…' : `已选 ${activeCart.checkedCount} ${isCoffee ? '杯' : '件'}` }}</text><text class="sum ink-price">¥{{ activeCart.checkedAmountText }}</text></view>
      <button class="ink-btn-primary settle-btn" :disabled="isCoffee ? !canCoffeeCheckout : !!goods.checkoutIssue" @click="goCreateOrder">{{ isCoffee && !context.coffeeStore?.capabilities.placeOrder ? '暂未开放' : '去结算' }}</button>
    </GlassActionBar>
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CartItems from '@/components/CartItems.vue'
import CoffeeCartItems from '@/components/CoffeeCartItems.vue'
import BusinessTabs from '@/components/BusinessTabs.vue'
import GlassActionBar from '@/components/GlassActionBar.vue'
import { requireLogin } from '@/utils/auth'
import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/storage'
import { getCoffeeStore } from '@/api/coffee'
import { coffeeOrderingText } from '@/utils/coffee'
import { useCartStore } from '@/stores/cart'
import { useCoffeeCartStore } from '@/stores/coffeeCart'
import { useBusinessContext, type BusinessType } from '@/stores/businessContext'
const goods = useCartStore(), coffee = useCoffeeCartStore(), context = useBusinessContext()
const auth = useAuthStore()
const authenticated = computed(() => !!auth.token), storeLoading = ref(false), storeError = ref('')
const isCoffee = computed(() => context.cartBusiness === 'COFFEE')
const activeCart = computed(() => isCoffee.value ? coffee : goods)
const canCoffeeCheckout = computed(() => !!context.coffeeStore?.capabilities.placeOrder && !!context.coffeeStore?.capabilities.payOnline && !storeLoading.value && !storeError.value && !coffee.busy && !coffee.loading && !coffee.error && coffee.checkedCount > 0)
const tabs = computed(() => [{ value: 'GOODS', label: '商品', count: goods.totalCount }, { value: 'COFFEE', label: '咖啡', count: coffee.totalCount }])
function login() { requireLogin('/pages/cart/index') }
async function refreshCoffee() {
  if (storeLoading.value) return
  storeLoading.value = true; storeError.value = ''
  try {
    let storeId = context.coffeeStoreId || coffee.storeId
    try { const store = await getCoffeeStore(); context.setCoffeeStore(store); storeId = store.id }
    catch (e) {
      // 目录停用后仍可维护原购物篮；旧能力失效，绝不据此开放下单。
      context.coffeeStore = null
      if (!storeId) throw e
    }
    await coffee.refresh(storeId)
  } catch (e: any) { storeError.value = e?.message || '门店暂不可用，请重试' }
  finally { storeLoading.value = false }
}
function refresh() { if (isCoffee.value) refreshCoffee(); else goods.refresh() }
function selectBusiness(value: string) { context.cartBusiness = value as BusinessType; storeError.value = ''; refresh() }
function goCreateOrder() {
  if (isCoffee.value) {
    if (canCoffeeCheckout.value && requireLogin('/pages/cart/index')) uni.navigateTo({ url: '/pages/coffee-checkout/index' })
    return
  }
  if (goods.prepareCheckout('/pages/cart/index')) uni.navigateTo({ url: '/pages/order-create/index' })
}
onShow(() => {
  coffee.syncAccount()
  if (!authenticated.value) return
  goods.refresh(); refreshCoffee()
})
</script>
<style scoped>
.header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.title { font-size: 40rpx; }
.refresh-control, .select-all { padding: 8rpx 0; background: transparent; font-size: 25rpx; line-height: 1.5; color: #637f51; flex-shrink: 0; }
.business-note { display: block; margin: 0 0 28rpx; color: #65755d; font-size: 24rpx; line-height: 1.7; }
.settle-total { flex: 1; min-width: 0; }
.sum-label { display: block; color: #65755d; font-size: 23rpx; }
.sum { display: block; font-size: 36rpx; margin-top: 4rpx; }
.settle-btn { width: 190rpx; flex: none; }
.state-action { margin-top: 24rpx; }
button[disabled] { opacity: .45; }
</style>
