<template>
  <view class="page business-page coffee-page" :class="{ 'page-hidden': !visible }">
    <view class="ink-page-content">
      <view class="store-card ink-card">
        <text class="page-kicker">COFFEE & DAILY MOMENTS</text>
        <text class="store-name">{{ store?.name || '咖啡点单' }}</text>
        <template v-if="store"><text class="store-status">{{ businessStatusText[store.businessStatus] }}</text><text class="store-detail">{{ store.address }} · {{ store.openingHours }}</text><text v-if="store.announcement" class="store-detail">{{ store.announcement }}</text></template>
        <text class="preparation-note">{{ coffeeOrderingText(store) }}</text>
      </view>
      <view v-if="loading" class="ink-loading"><view class="ink-loading-mark" /><text>正在查看菜单…</text></view>
      <view v-else-if="error" class="ink-empty"><text class="ink-empty-title">{{ unavailable ? '门店筹备中' : '菜单暂时未能加载' }}</text><text class="ink-empty-sub">{{ error }}</text><button class="ink-btn-secondary state-action" @click="loadMenu">重新加载</button></view>
      <view v-else-if="!menu?.products.length" class="ink-empty"><text class="ink-empty-title">菜单正在准备</text><text class="ink-empty-sub">稍后再来看看</text></view>
      <view v-else class="market">
        <scroll-view class="category-side" scroll-y><button class="category-item" :class="{ active: !categoryId }" @click="selectCategory('')">全部</button><button v-for="category in menu.categories" :key="category.id" class="category-item" :class="{ active: categoryId === category.id }" @click="selectCategory(category.id)">{{ category.name }}</button></scroll-view>
        <scroll-view class="goods-pane" scroll-y :scroll-top="paneScroll" @scroll="paneScroll = $event.detail.scrollTop">
          <view v-if="!products.length" class="ink-empty"><text>当前分类暂无饮品</text></view>
          <view v-for="product in products" :key="product.id" class="coffee-card ink-card">
            <image v-if="imageUrl(product.imageUrl)" class="coffee-cover" :src="imageUrl(product.imageUrl)" mode="aspectFill" /><view v-else class="coffee-cover placeholder">饮</view>
            <view class="coffee-meta"><text class="coffee-name">{{ product.name }}</text><text class="coffee-description">{{ product.description }}</text><view class="product-bottom"><text class="ink-price">{{ fromPrice(product) === null ? '已售罄' : `¥${cents(fromPrice(product)!)} 起` }}</text><button class="spec-button" :disabled="fromPrice(product) === null" @click="openSpec(product)">选规格</button></view></view>
          </view>
        </scroll-view>
      </view>
    </view>
    <view v-if="cartOpen" class="basket-mask" @click="cartOpen = false" />
    <view v-if="cartOpen" class="coffee-drawer"><view class="drawer-title"><text>咖啡篮</text><button aria-label="关闭咖啡篮" @click="cartOpen = false">×</button></view><text class="preparation-note">{{ coffeeOrderingText(store) }}</text><scroll-view class="drawer-content" scroll-y><CoffeeCartItems :authenticated="authenticated" compact @browse="cartOpen = false" /></scroll-view></view>
    <GlassActionBar business-nav><view class="basket-summary" @click="openCart"><text class="basket-title">咖啡篮 · {{ cart.totalCount }}</text><text class="basket-amount">{{ cart.loading ? '正在同步…' : cart.error ? '同步失败，点击重试' : `已选 ${cart.checkedCount} 杯 · ¥${cart.checkedAmountText}` }}</text></view><button class="ink-btn-primary unavailable-button" :disabled="!canCheckout" @click="checkout">{{ store?.capabilities.placeOrder ? '去结算' : '暂未开放' }}</button></GlassActionBar>
    <BusinessTabBar business="COFFEE" />
    <CoffeeSpecSheet :product="selectedProduct" :selected-id="selectedSkuId" :busy="cart.busy || cart.loading" :can-add="!loading && !error && !!store?.capabilities.addToCart" @select="selectedSkuId = $event" @close="selectedProduct = null" @add="addSelected" />
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { getCoffeeMenu, getCoffeeStore, type CoffeeMenu, type CoffeeProduct, type CoffeeStore } from '@/api/coffee'
import { useCoffeeCartStore } from '@/stores/coffeeCart'
import { useBusinessContext } from '@/stores/businessContext'
import { useAuthStore } from '@/stores/auth'
import { getToken } from '@/utils/storage'
import { requireLogin } from '@/utils/auth'
import { cents, fromPrice, imageUrl, coffeeOrderingText } from '@/utils/coffee'
import GlassActionBar from '@/components/GlassActionBar.vue'
import BusinessTabBar from '@/components/BusinessTabBar.vue'
import CoffeeSpecSheet from '@/components/CoffeeSpecSheet.vue'
import CoffeeCartItems from '@/components/CoffeeCartItems.vue'
const cart = useCoffeeCartStore(), context = useBusinessContext()
const store = ref<CoffeeStore | null>(null), menu = ref<CoffeeMenu | null>(null)
const auth = useAuthStore()
const authenticated = computed(() => !!auth.token)
const loading = ref(false), error = ref(''), unavailable = ref(false), visible = ref(true)
const categoryId = ref(''), paneScroll = ref(0), cartOpen = ref(false)
const selectedProduct = ref<CoffeeProduct | null>(null), selectedSkuId = ref('')
const businessStatusText = { OPEN: '营业中', CLOSED: '休息中', PAUSED: '暂停营业' }
const products = computed(() => (menu.value?.products || []).filter(p => !categoryId.value || p.categoryId === categoryId.value))
const canCheckout = computed(() => !!store.value?.capabilities.placeOrder && !!store.value?.capabilities.payOnline && !loading.value && !error.value && !cart.loading && !cart.busy && !cart.error && cart.checkedCount > 0)
function checkout() {
  if (!canCheckout.value || !requireLogin('/pages/coffee/index')) return
  cartOpen.value = false
  uni.navigateTo({ url: '/pages/coffee-checkout/index' })
}
function selectCategory(id: string) { categoryId.value = id; paneScroll.value = 0 }
async function loadMenu() {
  if (loading.value) return
  loading.value = true; error.value = ''; unavailable.value = false
  try {
    const value = await getCoffeeStore()
    store.value = value; context.setCoffeeStore(value)
    if (!value.capabilities.browse) throw new Error('门店目录暂未开放')
    menu.value = await getCoffeeMenu(value.id)
    // Public browsing never depends on a successful authenticated cart request.
    cart.refresh(value.id)
  } catch (e: any) {
    error.value = e?.message || '请稍后重试'; unavailable.value = e?.code === 90401
    if (context.coffeeStoreId) cart.refresh(context.coffeeStoreId)
  }
  finally { loading.value = false }
}
function openSpec(product: CoffeeProduct) {
  selectedProduct.value = product
  selectedSkuId.value = (product.skus.find(s => s.available) || product.skus[0])?.id || ''
}
async function addSelected() {
  const sku = selectedProduct.value?.skus.find(s => s.id === selectedSkuId.value)
  if (loading.value || error.value || !store.value?.capabilities.addToCart || !sku?.available || cart.busy || cart.loading) return
  const ok = await cart.add(store.value.id, sku.id)
  if (ok) { uni.showToast({ title: `已选 ${cart.checkedCount} 杯 · ¥${cart.checkedAmountText}`, icon: 'none' }); selectedProduct.value = null }
  else if (getToken()) {
    // Availability and price may have changed while the specification sheet was open.
    const selectedId = selectedProduct.value?.id
    await loadMenu()
    selectedProduct.value = !error.value ? menu.value?.products.find(p => p.id === selectedId) || null : null
  }
}
function openCart() { cartOpen.value = true; if (store.value) cart.refresh(store.value.id) }
onShow(() => {
  visible.value = true; context.cartBusiness = 'COFFEE'; cart.syncAccount()
  loadMenu()
})
onHide(() => { visible.value = false })
</script>
<style scoped>
.store-card { padding: 26rpx; margin-bottom: 28rpx; }
.page-kicker { display: block; color: #65755d; font-size: 18rpx; letter-spacing: 2rpx; }
.store-name { display: inline-block; font-size: 36rpx; font-weight: 600; margin: 10rpx 16rpx 8rpx 0; }
.store-status { font-size: 23rpx; color: #637f51; }
.store-detail, .preparation-note { display: block; color: #65755d; font-size: 23rpx; line-height: 1.7; }
.preparation-note { color: #637f51; margin-top: 12rpx; }
.market { display: flex; gap: 22rpx; }
.category-side { flex: none; width: 132rpx; }
.category-side, .goods-pane { height: calc(100vh - 580rpx - env(safe-area-inset-bottom)); min-height: 360rpx; }
.category-item { margin-bottom: 16rpx; padding: 20rpx 8rpx; color: #65755d; font-size: 25rpx; background: transparent; line-height: 1.5; }
.category-item::after { border: 0; }
.category-item.active { color: #637f51; background: #e8efdf; font-weight: 600; }
.goods-pane { flex: 1; min-width: 0; }
.coffee-card { display: flex; align-items: center; padding: 18rpx; margin-bottom: 20rpx; gap: 16rpx; }
.coffee-cover { width: 116rpx; height: 146rpx; border-radius: 16rpx; flex: none; background: #eef2e6; }
.placeholder { display: flex; align-items: center; justify-content: center; color: #637f51; font-size: 34rpx; }
.coffee-meta { flex: 1; min-width: 0; }
.coffee-name { display: block; font-size: 27rpx; font-weight: 600; }
.coffee-description { display: block; color: #65755d; font-size: 21rpx; margin: 8rpx 0; }
.product-bottom { display: flex; align-items: center; gap: 8rpx; flex-wrap: wrap; }
.product-bottom .ink-price { font-size: 29rpx; }
.spec-button { padding: 8rpx 14rpx; margin-left: auto; font-size: 22rpx; line-height: 1.6; color: #fff; background: #637f51; }
.basket-summary { flex: 1; min-width: 0; }
.basket-title, .basket-amount { display: block; }
.basket-title { font-size: 28rpx; }
.basket-amount { font-size: 23rpx; color: #65755d; margin-top: 8rpx; }
.unavailable-button { min-width: 180rpx; background: #829573; opacity: .7; }
.basket-mask { position: fixed; inset: 0; z-index: 35; background: rgba(29,40,24,.3); }
.coffee-drawer { position: fixed; left: 0; right: 0; bottom: calc(100rpx + env(safe-area-inset-bottom)); padding: 30rpx 30rpx 160rpx; border-radius: 36rpx 36rpx 0 0; background: #f7f9f2; z-index: 40; }
.drawer-title { display: flex; justify-content: space-between; align-items: center; font-size: 34rpx; }
.drawer-title button { background: transparent; line-height: 1.5; }
.drawer-content { height: min(60vh, 600rpx); }
.state-action { margin-top: 24rpx; }
</style>
