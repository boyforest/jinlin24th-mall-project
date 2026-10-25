<template>
  <view class="page goods-page business-page">
    <view class="ink-page-content">

      <view class="ink-search search-bar">
        <text class="ink-search-icon">茶</text>
        <input
          v-model="searchKeyword"
          class="search-input"
          confirm-type="search"
          placeholder="寻一味节气养生"
          maxlength="50"
          @confirm="submitSearch"
        />
        <text v-if="searchKeyword" class="search-clear" @click="clearSearch">清</text>
      </view>

      <view class="market">
        <scroll-view class="category-side" scroll-y>
          <view
            class="category-item"
            :class="{ active: selectedCategoryId === 0 }"
            @click="selectCategory(0)"
          >
            <text>全部</text>
            <text class="category-badge">养</text>
          </view>
          <view
            v-for="category in categories"
            :key="category.id"
            class="category-item"
            :class="{ active: selectedCategoryId === category.id }"
            @click="selectCategory(category.id)"
          >
            <text>{{ category.name }}</text>
            <text v-if="category.sort && category.sort <= 3" class="category-badge">荐</text>
          </view>
        </scroll-view>

        <scroll-view
          class="goods-pane"
          scroll-y
          refresher-enabled
          :refresher-triggered="refreshing"
          @refresherrefresh="refreshList"
          @scrolltolower="loadMore"
        >
          <view class="section-head">
            <view>
              <text class="section-kicker">SEASONAL PICKS</text>
              <text class="title ink-title">养物归集</text>
            </view>
            <text class="refresh" @click="refreshList">换一批</text>
          </view>

          <view v-if="loading && items.length === 0" class="ink-loading">
            <view class="ink-loading-mark"></view>
            <text>水墨晕染中...</text>
          </view>
          <view v-else-if="error" class="hint error">{{ error }}<button class="ink-btn-secondary" @click="refreshList">重新加载</button></view>
          <view v-else-if="items.length === 0" class="ink-empty ink-card">
            <view class="ink-empty-art"></view>
            <text class="ink-empty-title">{{ searchKeyword ? '未寻到此味' : '暂无养物' }}</text>
            <text class="ink-empty-sub">{{ searchKeyword ? '换一味草木，再寻节气' : '换个分类再看看' }}</text>
          </view>

          <view v-else class="goods-list">
            <view v-for="item in items" :key="item.id" class="goods-card ink-card" @click="goDetail(item.id)">
              <image v-if="item.mainImage" class="goods-cover" :src="item.mainImage" mode="aspectFill" lazy-load />
              <view class="goods-meta">
                <text class="goods-name ink-title">{{ item.name }}</text>
                <text class="goods-sub">{{ item.subtitle || '顺时滋补，草本新养' }}</text>
                <text class="goods-spec">{{ item.defaultSku?.skuName || '默认规格' }} · 库存 {{ item.defaultSku?.stock ?? '-' }}</text>
                <text class="goods-price ink-price">¥{{ skuPrice(item) }}</text>
                <text v-if="memberHint(item)" class="goods-member-hint">分销会员 ¥{{ money(item.defaultSku?.memberPrice) }}</text>
              </view>
              <button class="add-btn" :disabled="cart.busy || cart.loading || !(item.defaultSku?.stock)" :aria-label="item.defaultSku?.stock ? '加入购物车' : '暂时售罄'" @click.stop="quickAdd(item)">+</button>
            </view>
            <view v-if="loadingMore" class="more-tip">继续整理养物中...</view>
            <view v-else-if="finished" class="more-tip">已展示全部养物</view>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="cart-mask" v-if="cartOpen" @click="cartOpen = false"></view>
    <view class="cart-drawer" :class="{ open: cartOpen }">
      <view class="drawer-head">
        <text class="drawer-title ink-title">一篮养物</text>
        <button class="drawer-action drawer-select" :disabled="cart.busy || cart.loading || !!cart.error" @click="toggleAll">{{ cart.allChecked ? '取消全选' : '全选' }}</button>
      </view>
      <scroll-view class="drawer-list" scroll-y>
        <CartItems compact @browse="cartOpen = false" />
      </scroll-view>
    </view>

    <GlassActionBar business-nav><view class="floating-cart" @click="openCart">
      <view class="basket-mark">
        <text>篮</text>
        <text v-if="cart.totalCount" class="basket-count">{{ cart.totalCount }}</text>
      </view>
      <view class="basket-copy">
        <text class="basket-title">一篮养物</text>
        <text class="basket-sub">{{ cart.busy ? '正在更新养物…' : cart.error ? '养物加载失败，点击重试' : `已选 ${cart.checkedCount} 件 · ¥${cart.checkedAmountText}` }}</text>
      </view>
      <button class="checkout-btn" :disabled="cart.busy || cart.loading || !!cart.error" @click.stop="goCheckout">去结算</button>
    </view></GlassActionBar>

    <BusinessTabBar business="GOODS" />
  </view>
</template>

<script setup lang="ts">
import CartItems from '@/components/CartItems.vue'
import { onLoad, onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import BusinessTabBar from '@/components/BusinessTabBar.vue'
import GlassActionBar from '@/components/GlassActionBar.vue'
import { listProducts, listProductSkus, type ProductSkuVO, type ProductVO } from '@/api/product'
import { listCategories, type ProductCategoryVO } from '@/api/category'
import { useCartStore } from '@/stores/cart'
import { money, requireLogin } from '@/utils/auth'
import { useBusinessContext } from '@/stores/businessContext'

type ProductWithSku = ProductVO & { defaultSku?: ProductSkuVO }

const items = ref<ProductWithSku[]>([])
const categories = ref<ProductCategoryVO[]>([])
const selectedCategoryId = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const refreshing = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const error = ref('')
const cartOpen = ref(false)
const searchKeyword = ref('')
const cart = useCartStore()
const context = useBusinessContext()

async function ensureCategories() {
  if (categories.value.length === 0) {
    categories.value = await listCategories()
  }
}

async function loadList(reset = false) {
  if (loading.value || loadingMore.value) return
  if (!reset && finished.value) return
  error.value = ''
  if (reset) {
    page.value = 1
    finished.value = false
    loading.value = true
  } else {
    loadingMore.value = true
  }

  try {
    await ensureCategories()
    const pageData = await listProducts({
      page: page.value,
      size: pageSize,
      categoryId: selectedCategoryId.value || undefined,
      keyword: searchKeyword.value.trim() || undefined,
    })
    const records = await attachDefaultSkus(pageData?.records || [])
    items.value = reset ? records : [...items.value, ...records]
    const total = Number(pageData?.total || 0)
    finished.value = items.value.length >= total || records.length < pageSize
    page.value += 1
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
    loadingMore.value = false
    refreshing.value = false
    uni.stopPullDownRefresh()
  }
}

async function attachDefaultSkus(products: ProductVO[]) {
  const result = await Promise.all(products.map(async (product) => {
    try {
      const skus = await listProductSkus(product.id)
      const defaultSku = skus.find((sku) => (sku.stock || 0) > 0) || skus[0]
      return { ...product, defaultSku }
    } catch {
      return product
    }
  }))
  return result
}

function refreshList() {
  refreshing.value = true
  loadList(true)
}

function loadMore() {
  loadList(false)
}

function selectCategory(categoryId: number) {
  if (selectedCategoryId.value === categoryId) return
  selectedCategoryId.value = categoryId
  loadList(true)
}

function submitSearch() {
  loadList(true)
}

function clearSearch() {
  searchKeyword.value = ''
  loadList(true)
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/product-detail/index?id=${id}` })
}

/**
 * 是否展示"分销会员 ¥X"提示（方案①的营销钩子）。
 * 只在确实有优惠、且当前用户拿到的不是会员价时展示。
 */
function memberHint(item: ProductWithSku) {
  const sku = item.defaultSku
  const member = Number(sku?.memberPrice ?? 0)
  const list = Number(sku?.price ?? 0)
  const effective = Number(sku?.effectivePrice ?? list)
  return member > 0 && member < list && effective !== member
}

function skuPrice(item: ProductWithSku) {
  // 成交价一律用后端下发的 effectivePrice；memberPrice 只用于展示会员价提示
  return money(item.defaultSku?.effectivePrice ?? item.defaultSku?.price)
}

async function quickAdd(item: ProductWithSku) {
  if (cart.busy || cart.loading) return
  const skuId = item.defaultSku?.id
  if (!skuId || !item.defaultSku?.stock) {
    uni.showToast({ title: '暂无可售规格', icon: 'none' })
    return
  }
  try {
    const ok = await cart.add(item.id, skuId, 1, '/pages/goods/index')
    if (ok) uni.showToast({ title: '已加入一篮养物', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加入失败，请重试', icon: 'none' })
  }
}

function openCart() {
  if (!requireLogin('/pages/goods/index')) return
  cartOpen.value = true
  cart.refresh()
}

function toggleAll() { cart.setAllChecked(cart.allChecked ? 0 : 1) }

function goCheckout() {
  if (cart.prepareCheckout('/pages/goods/index')) {
    cartOpen.value = false
    uni.navigateTo({ url: '/pages/order-create/index' })
  }
}

onLoad((options) => {
  if (options?.categoryId) selectedCategoryId.value = Number(options.categoryId)
})
onShow(() => {
  context.cartBusiness = 'GOODS'
  if (items.value.length === 0) loadList(true).catch(() => {})
  cart.refresh('/pages/goods/index').catch(() => {})
})

onPullDownRefresh(refreshList)
onReachBottom(loadMore)
</script>

<style scoped>
.drawer-select { margin: 0; padding: 8rpx 0 8rpx 20rpx; background: transparent; line-height: 1.5; }
.drawer-select::after { border: 0; }
button[disabled] { opacity: .45; }

.goods-page {
  padding-bottom: 154rpx;
}
.search-bar {
  margin-bottom: 24rpx;
}
.search-input {
  flex: 1;
  min-width: 0;
  color: #6f7b68;
  font-size: 26rpx;
}
.search-clear {
  flex: 0 0 auto;
  color: #5f8f4b;
  font-size: 24rpx;
  font-weight: 700;
}
.market {
  display: flex;
  align-items: flex-start;
  gap: 22rpx;
}
.category-side {
  width: 142rpx;
  height: 920rpx;
  flex: none;
}
.category-item {
  position: relative;
  min-height: 96rpx;
  padding: 20rpx 10rpx;
  margin-bottom: 16rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5f665d;
  font-family: "STKaiti", "KaiTi", "Songti SC", "STSong", serif;
  font-size: 26rpx;
  text-align: center;
  background: rgba(255, 255, 255, 0.78);
  border: 1rpx solid rgba(111, 159, 88, 0.14);
}
.category-item.active {
  color: #1e2d1a;
  font-weight: 700;
  border-color: rgba(111, 159, 88, 0.42);
  background:
    radial-gradient(circle at 20% 14%, rgba(255, 255, 255, 0.88), transparent 34%),
    linear-gradient(135deg, rgba(111, 159, 88, 0.32), rgba(200, 224, 190, 0.56));
  box-shadow: 0 4rpx 16rpx rgba(111, 159, 88, 0.12);
}
.category-badge {
  position: absolute;
  right: -6rpx;
  top: -6rpx;
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 6rpx;
  border-radius: 999rpx;
  background: #b0493f;
  color: #fff;
  font-size: 18rpx;
  line-height: 30rpx;
}
.goods-pane {
  flex: 1;
  height: 920rpx;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}
.section-kicker {
  display: block;
  margin-bottom: 4rpx;
  color: #7f9f52;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 18rpx;
}
.title {
  display: block;
  font-size: 34rpx;
}
.refresh {
  color: #5f8f4b;
  font-size: 24rpx;
}
.hint {
  padding: 24rpx;
  color: #666;
}
.hint.error {
  color: #c45450;
}
.goods-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding-bottom: 28rpx;
}
.goods-card {
  display: flex;
  align-items: center;
  min-height: 210rpx;
  padding: 18rpx;
  animation: card-rise 0.5s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}
.goods-card:nth-child(1) { animation-delay: 0.04s; }
.goods-card:nth-child(2) { animation-delay: 0.09s; }
.goods-card:nth-child(3) { animation-delay: 0.14s; }
.goods-card:nth-child(4) { animation-delay: 0.19s; }
.goods-card:nth-child(5) { animation-delay: 0.24s; }

@keyframes card-rise {
  from { opacity: 0; transform: translateY(18rpx); }
  to   { opacity: 1; transform: translateY(0); }
}
.goods-cover {
  position: relative;
  z-index: 1;
  width: 160rpx;
  height: 160rpx;
  border-radius: 18rpx;
  background: #eef6eb;
  flex: none;
}
.goods-meta {
  position: relative;
  z-index: 1;
  min-width: 0;
  flex: 1;
  padding-left: 18rpx;
}
.goods-name {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.35;
}
.goods-sub,
.goods-spec {
  display: block;
  color: #6f7b68;
  font-size: 23rpx;
  margin-top: 8rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.goods-member-hint {
  display: block;
  font-size: 20rpx;
  color: #b8860b;
}

.goods-price {
  display: block;
  margin-top: 12rpx;
  font-size: 32rpx;
}
.add-btn {
  position: relative;
  z-index: 1;
  width: 58rpx;
  height: 58rpx;
  flex: none;
  padding: 0;
  border-radius: 50%;
  line-height: 56rpx;
  color: #fff;
  background: #5f8f4b;
  font-size: 36rpx;
  box-shadow: 0 8rpx 18rpx rgba(95, 143, 75, 0.24);
  transition: all 0.18s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.add-btn:active {
  transform: scale(0.82);
  background: #4f7b42;
}
.more-tip,
.drawer-empty {
  padding: 24rpx 0;
  color: #6f7b68;
  text-align: center;
  font-size: 24rpx;
}
.cart-mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  background: rgba(21, 29, 19, 0.28);
}
.cart-drawer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: var(--window-bottom, 0px);
  z-index: 21;
  height: 0;
  padding: 0 32rpx;
  border-radius: 30rpx 30rpx 0 0;
  background: rgba(253, 253, 251, 0.98);
  box-shadow: 0 -14rpx 36rpx rgba(79, 123, 66, 0.16);
  overflow: hidden;
  transition: height 0.28s cubic-bezier(0.32, 0.72, 0, 0.95);
}
.cart-drawer.open {
  height: 720rpx;
  padding-top: 28rpx;
  padding-bottom: 138rpx;
}
.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}
.drawer-title {
  font-size: 36rpx;
}
.drawer-action {
  color: #5f8f4b;
  font-size: 25rpx;
}
.drawer-list {
  height: 520rpx;
}
.floating-cart {
  position: fixed;
  left: 28rpx;
  right: 28rpx;
  bottom: calc(28rpx + var(--window-bottom, 0px));
  z-index: 30;
  height: 108rpx;
  padding: 12rpx 18rpx 12rpx 14rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  background: rgba(253, 252, 248, 0.94);
  border: 1rpx solid rgba(111, 159, 88, 0.16);
  box-shadow:
    0 10rpx 32rpx rgba(79, 123, 66, 0.1),
    0 2rpx 8rpx rgba(45, 45, 45, 0.04);
  backdrop-filter: blur(12rpx);
  transition: box-shadow 0.35s ease;
}
.basket-mark {
  position: relative;
  width: 82rpx;
  height: 82rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(253, 251, 247, 0.9);
  border: 2.5rpx double rgba(79, 123, 66, 0.42);
  color: #4f7b42;
  font-family: "STKaiti", "KaiTi", "Songti SC", "STSong", serif;
  font-size: 28rpx;
  box-shadow: inset 0 0 0 1rpx rgba(79, 123, 66, 0.08);
  animation: seal-glow 3s ease-in-out infinite;
}
@keyframes seal-glow {
  0%, 100% { box-shadow: inset 0 0 0 1rpx rgba(79, 123, 66, 0.04); }
  50%      { box-shadow: inset 0 0 0 1rpx rgba(79, 123, 66, 0.14), 0 0 12rpx rgba(111, 159, 88, 0.08); }
}
.basket-count {
  position: absolute;
  right: -4rpx;
  top: -4rpx;
  min-width: 28rpx;
  height: 28rpx;
  padding: 0 5rpx;
  border-radius: 999rpx;
  background: #b64f45;
  color: #fff;
  font-size: 16rpx;
  line-height: 28rpx;
  text-align: center;
  box-shadow: 0 1rpx 4rpx rgba(182, 79, 69, 0.3);
}
.basket-copy {
  flex: 1;
  padding-left: 18rpx;
}
.basket-title {
  display: block;
  color: #2d2d2d;
  font-size: 28rpx;
  letter-spacing: 1rpx;
}
.basket-sub {
  display: block;
  margin-top: 4rpx;
  color: #6f7b68;
  font-size: 22rpx;
}
.checkout-btn {
  width: 160rpx;
  height: 72rpx;
  border-radius: 18rpx;
  line-height: 72rpx;
  color: #fff;
  background: linear-gradient(135deg, #5f8f4b, #86c166);
  font-size: 26rpx;
  font-weight: 500;
  box-shadow: 0 4rpx 14rpx rgba(95, 143, 75, 0.22);
  transition: all 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
}
.checkout-btn:active {
  transform: scale(0.96);
  box-shadow: 0 2rpx 8rpx rgba(95, 143, 75, 0.14);
}

.goods-page { padding-bottom: calc(300rpx + env(safe-area-inset-bottom)); }
.category-side, .goods-pane { height: calc(100vh - 390rpx - env(safe-area-inset-bottom)); min-height: 400rpx; }
.goods-pane { min-width: 0; }
.goods-card { background: #fffef9; box-shadow: none; animation-duration: 210ms; }
.category-item { font-family: inherit; background: transparent; border: 0; }
.category-item.active { color: #637f51; background: #e8efdf; box-shadow: none; }
.floating-cart { position: static; height: auto; width: 100%; padding: 0; background: transparent; border: 0; box-shadow: none; backdrop-filter: none; }
.basket-mark { animation: none; }
.checkout-btn { background: #637f51; }
.cart-drawer { bottom: calc(100rpx + env(safe-area-inset-bottom)); }
.cart-drawer.open { height: min(740rpx, 72vh); }
@media (prefers-reduced-motion: reduce) { * { animation: none !important; transition: none !important; } }
</style>
