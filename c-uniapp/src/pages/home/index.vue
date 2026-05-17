<template>
  <view class="page home-page">
    <view class="ink-page-content">
      <view class="hero">
        <view class="brand-row">
          <image class="official-logo" src="/static/brand/official-logo.png" mode="widthFix" />
        </view>
        <view class="solar-term">
          <text class="lunar-date">{{ currentTerm.seasonText }}</text>
          <text class="solar-date">{{ currentTermDate }}</text>
          <view class="term-center">
            <text class="term-cn ink-title">{{ currentTerm.chars[0] }}</text>
            <text class="term-en">{{ currentTerm.english }}</text>
            <view class="term-bottom">
              <text class="term-cn ink-title">{{ currentTerm.chars[1] }}</text>
              <text class="term-stamp">节气</text>
            </view>
          </view>
          <view class="term-copy">
            <text class="term-poem">{{ currentTerm.poem }}</text>
            <text class="term-poem-en">{{ currentTerm.poemEn }}</text>
          </view>
        </view>
      </view>

      <view class="ink-search search-bar">
        <text class="ink-search-icon">茶</text>
        <input
          v-model="searchKeyword"
          class="search-input"
          confirm-type="search"
          placeholder="寻一味节气养生"
          @confirm="submitSearch"
        />
        <text v-if="searchKeyword" class="search-clear" @click="clearSearch">清</text>
      </view>

      <view v-if="notices.length" class="notice-strip" @click="openActivity(notices[0])">
        <text class="notice-mark">告</text>
        <text class="notice-text">{{ notices[0].title }}</text>
      </view>

      <swiper v-if="banners.length" class="activity-swiper" circular autoplay interval="4200">
        <swiper-item v-for="activity in banners" :key="activity.id">
          <view class="activity-card" @click="openActivity(activity)">
            <image v-if="activity.imageUrl" class="activity-image" :src="activity.imageUrl" mode="aspectFill" />
            <view class="activity-copy">
              <text class="activity-title ink-title">{{ activity.title }}</text>
              <text v-if="activity.subtitle" class="activity-sub">{{ activity.subtitle }}</text>
            </view>
          </view>
        </swiper-item>
      </swiper>

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
          <view v-else-if="error" class="hint error">{{ error }}</view>
          <view v-else-if="items.length === 0" class="ink-empty ink-card">
            <view class="ink-empty-art"></view>
            <text class="ink-empty-title">暂无养物</text>
            <text class="ink-empty-sub">换个分类再看看</text>
          </view>

          <view v-else class="goods-list">
            <view v-for="item in items" :key="item.id" class="goods-card ink-card" @click="goDetail(item.id)">
              <image v-if="item.mainImage" class="goods-cover" :src="item.mainImage" mode="aspectFill" />
              <view v-else class="goods-cover placeholder">JL</view>
              <view class="goods-meta">
                <text class="goods-name ink-title">{{ item.name }}</text>
                <text class="goods-sub">{{ item.subtitle || '顺时滋补，草本新养' }}</text>
                <text class="goods-spec">{{ item.defaultSku?.skuName || '默认规格' }} · 库存 {{ item.defaultSku?.stock ?? '-' }}</text>
                <text class="goods-price ink-price">¥{{ skuPrice(item) }}</text>
              </view>
              <button class="add-btn" @click.stop="quickAdd(item)">+</button>
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
        <text class="drawer-action" @click="toggleAll">{{ cart.allChecked ? '取消全选' : '全选' }}</text>
      </view>
      <scroll-view class="drawer-list" scroll-y>
        <view v-if="cart.items.length === 0" class="drawer-empty">暂无养物</view>
        <view v-for="item in cart.items" :key="item.id" class="drawer-item">
          <text class="check" :class="{ checked: item.checked !== 0 }" @click="cart.setChecked(item, item.checked === 0 ? 1 : 0)">✓</text>
          <image v-if="item.productMainImage" class="drawer-cover" :src="item.productMainImage" mode="aspectFill" />
          <view class="drawer-meta">
            <text class="drawer-name">{{ item.productName }}</text>
            <text class="drawer-spec">{{ item.skuName }}</text>
            <text class="drawer-price ink-price">¥{{ money(item.memberPrice || item.price) }}</text>
          </view>
          <view class="drawer-stepper">
            <text class="step" @click="changeCartQty(item, -1)">-</text>
            <text class="qty">{{ item.quantity }}</text>
            <text class="step" @click="changeCartQty(item, 1)">+</text>
          </view>
          <text class="remove" @click="cart.remove(item.id)">删</text>
        </view>
      </scroll-view>
    </view>

    <view class="floating-cart" @click="cartOpen = true">
      <view class="basket-mark">
        <text>篮</text>
        <text v-if="cart.totalCount" class="basket-count">{{ cart.totalCount }}</text>
      </view>
      <view class="basket-copy">
        <text class="basket-title">一篮养物</text>
        <text class="basket-sub">已选 {{ cart.checkedCount }} 件 · ¥{{ cart.checkedAmountText }}</text>
      </view>
      <button class="checkout-btn" @click.stop="goCheckout">去结算</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { listProducts, listProductSkus, type ProductSkuVO, type ProductVO } from '@/api/product'
import { listCategories, type ProductCategoryVO } from '@/api/category'
import { listMarketingActivities, type MarketingActivityVO } from '@/api/marketing'
import type { CartVO } from '@/api/cart'
import { useCartStore } from '@/stores/cart'
import { money, requireLogin } from '@/utils/auth'
import { formatSolarTermDate, getCurrentSolarTerm } from '@/utils/solarTerms'

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
const banners = ref<MarketingActivityVO[]>([])
const notices = ref<MarketingActivityVO[]>([])
const cart = useCartStore()
const currentTerm = computed(() => getCurrentSolarTerm())
const currentTermDate = computed(() => formatSolarTermDate(currentTerm.value))

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

async function loadActivities() {
  try {
    const [bannerData, noticeData] = await Promise.all([
      listMarketingActivities('home_banner'),
      listMarketingActivities('home_notice'),
    ])
    banners.value = bannerData || []
    notices.value = noticeData || []
  } catch {
    banners.value = []
    notices.value = []
  }
}

function openActivity(activity: MarketingActivityVO) {
  if (activity.linkType === 'product' && activity.linkValue) {
    uni.navigateTo({ url: `/pages/product-detail/index?id=${activity.linkValue}` })
    return
  }
  if (activity.linkType === 'category' && activity.linkValue) {
    selectedCategoryId.value = Number(activity.linkValue)
    loadList(true)
    return
  }
  if (activity.linkType === 'page' && activity.linkValue) {
    uni.navigateTo({ url: activity.linkValue })
  }
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/product-detail/index?id=${id}` })
}

function skuPrice(item: ProductWithSku) {
  return money(item.defaultSku?.memberPrice || item.defaultSku?.price)
}

async function quickAdd(item: ProductWithSku) {
  const skuId = item.defaultSku?.id
  if (!skuId) {
    uni.showToast({ title: '暂无可售规格', icon: 'none' })
    return
  }
  const ok = await cart.add(item.id, skuId, 1, '/pages/home/index')
  if (ok) uni.showToast({ title: '已加入一篮养物', icon: 'success' })
}

async function changeCartQty(item: CartVO, delta: number) {
  const next = Number(item.quantity || 1) + delta
  if (next <= 0) {
    await cart.remove(item.id)
    return
  }
  await cart.setQuantity(item, next)
}

function toggleAll() {
  cart.setAllChecked(cart.allChecked ? 0 : 1)
}

function goCheckout() {
  if (!requireLogin('/pages/home/index')) return
  const first = cart.checkedItems[0]
  if (!first) {
    uni.showToast({ title: '请先选择养物', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/order-create/index?skuId=${first.skuId}` })
}

onShow(() => {
  if (items.value.length === 0) loadList(true)
  loadActivities()
  cart.refresh('/pages/home/index').catch(() => {})
})

onPullDownRefresh(refreshList)
onReachBottom(loadMore)
</script>

<style scoped>
.home-page {
  padding-bottom: 154rpx;
}
.hero {
  min-height: 1040rpx;
  padding: 48rpx 34rpx 52rpx;
  border-radius: 30rpx;
  background:
    radial-gradient(circle at 18% 7%, rgba(255, 255, 255, 0.95), transparent 34%),
    radial-gradient(circle at 88% 5%, rgba(172, 219, 160, 0.36), transparent 32%),
    linear-gradient(180deg, rgba(238, 248, 232, 0.72), rgba(255, 255, 255, 0.9) 76%),
    #f4f9f0;
  box-shadow: 0 14rpx 42rpx rgba(79, 123, 66, 0.13);
  overflow: hidden;
  position: relative;
  margin-bottom: 28rpx;
}
.hero::before {
  content: "";
  position: absolute;
  right: -260rpx;
  top: 92rpx;
  width: 960rpx;
  height: 1360rpx;
  border-radius: 46% 54% 44% 56%;
  background:
    linear-gradient(118deg, transparent 4%, rgba(255, 255, 255, 0.18) 24%, rgba(113, 177, 94, 0.26) 42%, rgba(225, 242, 218, 0.46) 56%, rgba(255, 255, 255, 0.28) 69%, transparent 94%),
    linear-gradient(142deg, transparent 0%, rgba(255, 255, 255, 0.34) 46%, rgba(79, 123, 66, 0.08) 56%, transparent 100%);
  transform: rotate(31deg);
}
.hero::after {
  content: "";
  position: absolute;
  left: 78rpx;
  bottom: 154rpx;
  width: 530rpx;
  height: 160rpx;
  border-bottom: 3rpx solid rgba(45, 45, 45, 0.1);
  border-radius: 50%;
  transform: rotate(-9deg);
}
.brand-row,
.solar-term,
.term-center,
.term-copy {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.brand-row {
  opacity: 0.82;
}
.official-logo {
  width: 520rpx;
  opacity: 0.72;
}
.solar-term {
  margin-top: 160rpx;
}
.lunar-date {
  color: rgba(45, 45, 45, 0.8);
  font-family: "Songti SC", "STSong", "SimSun", serif;
  font-size: 24rpx;
  letter-spacing: 4rpx;
  margin-bottom: 10rpx;
}
.solar-date {
  color: #2d2d2d;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 42rpx;
  font-weight: 600;
  letter-spacing: 4rpx;
}
.term-center {
  margin-top: 154rpx;
}
.term-cn {
  color: #101410;
  font-family: "STKaiti", "KaiTi", "Songti SC", "STSong", serif;
  font-size: 132rpx;
  font-weight: 700;
  line-height: 0.92;
}
.term-en {
  margin: 24rpx 0 20rpx;
  color: #111;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 44rpx;
  letter-spacing: 2rpx;
}
.term-bottom {
  display: flex;
  align-items: center;
}
.term-stamp {
  margin-left: 14rpx;
  width: 42rpx;
  height: 64rpx;
  border: 2rpx solid #b0493f;
  border-radius: 6rpx;
  color: #b0493f;
  font-family: "Songti SC", "STSong", serif;
  font-size: 20rpx;
  line-height: 30rpx;
  text-align: center;
}
.term-copy {
  margin-top: 210rpx;
}
.term-poem {
  color: #2d2d2d;
  font-family: "Songti SC", "STSong", serif;
  font-size: 28rpx;
  letter-spacing: 14rpx;
}
.term-poem-en {
  margin-top: 24rpx;
  color: rgba(45, 45, 45, 0.46);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 22rpx;
  text-align: center;
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
.notice-strip {
  display: flex;
  align-items: center;
  gap: 14rpx;
  min-height: 68rpx;
  padding: 0 22rpx;
  margin-bottom: 22rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.78);
  border: 1rpx solid rgba(111, 159, 88, 0.18);
  box-shadow: 0 8rpx 24rpx rgba(79, 123, 66, 0.08);
}
.notice-mark {
  flex: 0 0 auto;
  width: 38rpx;
  height: 38rpx;
  line-height: 38rpx;
  text-align: center;
  border-radius: 50%;
  background: rgba(111, 159, 88, 0.13);
  color: #4f7b42;
  font-family: "Songti SC", "STSong", serif;
  font-size: 22rpx;
  font-weight: 700;
}
.notice-text {
  min-width: 0;
  flex: 1;
  color: #314d35;
  font-size: 26rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.activity-swiper {
  height: 176rpx;
  margin-bottom: 24rpx;
}
.activity-card {
  position: relative;
  height: 176rpx;
  overflow: hidden;
  border-radius: 22rpx;
  background:
    linear-gradient(135deg, rgba(233, 245, 226, 0.96), rgba(255, 255, 255, 0.9)),
    #f1f7ed;
  border: 1rpx solid rgba(111, 159, 88, 0.16);
}
.activity-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0.36;
}
.activity-copy {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
  padding: 0 30rpx;
}
.activity-title {
  color: #23482f;
  font-size: 34rpx;
}
.activity-sub {
  margin-top: 8rpx;
  color: #6d7c67;
  font-size: 24rpx;
}
.market {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
}
.category-side {
  width: 120rpx;
  height: 920rpx;
  flex: none;
}
.category-item {
  position: relative;
  min-height: 92rpx;
  padding: 18rpx 8rpx;
  margin-bottom: 14rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5f665d;
  font-family: "Songti SC", "STSong", serif;
  font-size: 25rpx;
  text-align: center;
  background: rgba(255, 255, 255, 0.72);
  border: 1rpx solid rgba(111, 159, 88, 0.14);
}
.category-item.active {
  color: #263322;
  font-weight: 700;
  background:
    radial-gradient(circle at 20% 14%, rgba(255, 255, 255, 0.8), transparent 34%),
    linear-gradient(135deg, rgba(111, 159, 88, 0.28), rgba(207, 226, 200, 0.54));
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
  gap: 18rpx;
  padding-bottom: 28rpx;
}
.goods-card {
  display: flex;
  align-items: center;
  min-height: 210rpx;
  padding: 16rpx;
}
.goods-cover {
  position: relative;
  z-index: 1;
  width: 156rpx;
  height: 156rpx;
  border-radius: 12rpx;
  background: #eef6eb;
  flex: none;
}
.goods-cover.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5f8f4b;
  font-family: Georgia, "Times New Roman", serif;
  font-weight: 700;
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
  font-size: 30rpx;
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
.goods-price {
  display: block;
  margin-top: 10rpx;
  font-size: 30rpx;
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
  bottom: 0;
  z-index: 21;
  height: 0;
  padding: 0 32rpx;
  border-radius: 30rpx 30rpx 0 0;
  background: rgba(253, 253, 251, 0.98);
  box-shadow: 0 -14rpx 36rpx rgba(79, 123, 66, 0.16);
  overflow: hidden;
  transition: height 0.22s ease;
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
.drawer-item {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid rgba(111, 159, 88, 0.12);
}
.check {
  width: 38rpx;
  height: 38rpx;
  margin-right: 12rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(111, 159, 88, 0.38);
  color: transparent;
  font-size: 24rpx;
  line-height: 34rpx;
  text-align: center;
}
.check.checked {
  color: #fff;
  background: #5f8f4b;
}
.drawer-cover {
  width: 92rpx;
  height: 92rpx;
  border-radius: 12rpx;
  background: #eef6eb;
  flex: none;
}
.drawer-meta {
  min-width: 0;
  flex: 1;
  padding-left: 14rpx;
}
.drawer-name,
.drawer-spec,
.drawer-price {
  display: block;
}
.drawer-name {
  font-size: 26rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.drawer-spec {
  color: #6f7b68;
  font-size: 22rpx;
  margin: 4rpx 0;
}
.drawer-stepper {
  display: flex;
  align-items: center;
  border: 1rpx solid rgba(111, 159, 88, 0.25);
  border-radius: 999rpx;
}
.step,
.qty {
  min-width: 44rpx;
  height: 44rpx;
  line-height: 44rpx;
  text-align: center;
}
.remove {
  margin-left: 10rpx;
  color: #b0493f;
  font-size: 22rpx;
}
.floating-cart {
  position: fixed;
  left: 28rpx;
  right: 28rpx;
  bottom: 24rpx;
  z-index: 30;
  height: 104rpx;
  padding: 14rpx 16rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  background: rgba(43, 56, 38, 0.96);
  box-shadow: 0 14rpx 34rpx rgba(43, 56, 38, 0.24);
}
.basket-mark {
  position: relative;
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef6eb;
  color: #5f8f4b;
  font-family: "Songti SC", "STSong", serif;
}
.basket-count {
  position: absolute;
  right: -4rpx;
  top: -8rpx;
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 6rpx;
  border-radius: 999rpx;
  background: #b0493f;
  color: #fff;
  font-size: 18rpx;
  line-height: 30rpx;
  text-align: center;
}
.basket-copy {
  flex: 1;
  padding-left: 18rpx;
}
.basket-title {
  display: block;
  color: #fff;
  font-size: 28rpx;
}
.basket-sub {
  display: block;
  margin-top: 4rpx;
  color: rgba(255, 255, 255, 0.68);
  font-size: 22rpx;
}
.checkout-btn {
  width: 166rpx;
  height: 68rpx;
  border-radius: 999rpx;
  line-height: 68rpx;
  color: #263322;
  background: #d8ebce;
  font-size: 26rpx;
}
</style>
