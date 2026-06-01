<template>
  <view class="page">
    <view class="ink-page-content">
      <view v-if="loading" class="ink-loading">
        <view class="ink-loading-mark"></view>
        <text>水墨晕染中...</text>
      </view>
      <view v-else-if="error" class="hint error">{{ error }}</view>

      <view v-else class="content">
        <view class="gallery ink-card">
          <swiper v-if="galleryImages.length" class="cover-swiper" circular indicator-dots>
            <swiper-item v-for="image in galleryImages" :key="image">
              <image class="cover" :src="image" mode="aspectFill" lazy-load @click="previewImage(image)" />
            </swiper-item>
          </swiper>
          <view v-else class="cover placeholder">JL</view>
          <text class="ink-tag gallery-tag">热销</text>
        </view>
        <view class="product-head">
          <text class="eyebrow">二十四养 · 节气滋补</text>
          <view class="title-row">
            <text class="title ink-title">{{ product?.name }}</text>
            <text class="small-seal">养</text>
          </view>
          <view v-if="product?.subtitle" class="sub">{{ product.subtitle }}</view>
        </view>

        <view class="sku-panel ink-card">
          <view class="panel-head">
            <text class="panel-title ink-title">选择规格</text>
            <text class="panel-sub">择其味，顺其时</text>
          </view>
          <view v-if="skus.length === 0" class="empty">暂无内容</view>
          <view
            v-for="sku in skus"
            :key="sku.id"
            class="sku"
            :class="{ active: selectedSkuId === sku.id, disabled: !sku.stock }"
            @click="selectSku(sku.id)"
          >
            <view>
              <text class="sku-name">{{ sku.skuName }}</text>
              <text class="sku-stock">余量 {{ sku.stock || 0 }}</text>
            </view>
            <text class="price ink-price">¥{{ money(sku.memberPrice || sku.price) }}</text>
          </view>
        </view>

        <view v-if="product?.effects || product?.precautions" class="care-panel ink-card">
          <view v-if="product?.effects" class="care-block">
            <text class="panel-title ink-title">功效说明</text>
            <text class="care-text">{{ product.effects }}</text>
          </view>
          <view v-if="product?.precautions" class="care-block">
            <text class="panel-title ink-title">注意事项</text>
            <text class="care-text">{{ product.precautions }}</text>
          </view>
        </view>

        <view v-if="product?.detail" class="detail ink-card">
          <view class="panel-head">
            <text class="panel-title ink-title">商品详情</text>
            <text class="panel-sub">一物一味</text>
          </view>
          <rich-text :nodes="product.detail" />
        </view>

        <view class="actions">
          <button class="ink-btn-secondary" @click="addToCart">加入一篮养物</button>
          <button class="ink-btn-primary" @click="goOrderCreate">立即购买</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { getProduct, listProductSkus, type ProductSkuVO, type ProductVO } from '@/api/product'
import { useCartStore } from '@/stores/cart'
import { money, requireLogin } from '@/utils/auth'

const id = ref<number>(0)
const product = ref<ProductVO | null>(null)
const skus = ref<ProductSkuVO[]>([])
const selectedSkuId = ref<number>(0)
const loading = ref(false)
const error = ref('')
const cart = useCartStore()
const galleryImages = computed(() => {
  const images = String(product.value?.images || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
  if (product.value?.mainImage && !images.includes(product.value.mainImage)) {
    images.unshift(product.value.mainImage)
  }
  return images
})

async function load() {
  if (!id.value) return
  loading.value = true
  error.value = ''
  try {
    const [productData, skuData] = await Promise.all([getProduct(id.value), listProductSkus(id.value)])
    product.value = productData
    skus.value = skuData || []
    selectedSkuId.value = skus.value.find((item) => (item.stock || 0) > 0)?.id || skus.value[0]?.id || 0
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function selectSku(skuId: number) {
  const sku = skus.value.find((item) => item.id === skuId)
  if (!sku || !sku.stock) return
  selectedSkuId.value = skuId
}

function previewImage(current: string) {
  uni.previewImage({
    urls: galleryImages.value,
    current,
  })
}

async function addToCart() {
  if (!requireLogin(`/pages/product-detail/index?id=${id.value}`)) return
  if (!selectedSkuId.value) {
    uni.showToast({ title: '请选择规格', icon: 'none' })
    return
  }
  try {
    await cart.add(id.value, selectedSkuId.value, 1, `/pages/product-detail/index?id=${id.value}`)
    uni.showToast({ title: '已加入一篮养物', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加入失败', icon: 'none' })
  }
}

function goOrderCreate() {
  if (!requireLogin(`/pages/product-detail/index?id=${id.value}`)) return
  if (!selectedSkuId.value) {
    uni.showToast({ title: '请选择规格', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/order-create/index?productId=${id.value}&skuId=${selectedSkuId.value}` })
}

onLoad((options) => {
  id.value = Number((options as any)?.id || 0)
  load()
})
</script>

<style scoped>
.hint {
  padding: 24rpx;
  color: #6f7b68;
}
.hint.error {
  color: #c45450;
}
.gallery {
  padding: 18rpx;
  margin-bottom: 28rpx;
}
.cover,
.cover-swiper {
  width: 100%;
  height: 520rpx;
}
.cover {
  border-radius: 20rpx;
  background: #f0f7ef;
}
.cover.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5f8f4b;
  font-family: "Songti SC", "STSong", serif;
  font-size: 52rpx;
  font-weight: 700;
}
.gallery-tag {
  position: absolute;
  left: 36rpx;
  top: 36rpx;
}
.product-head {
  margin-bottom: 28rpx;
}
.eyebrow {
  display: block;
  color: #7f9f52;
  font-size: 22rpx;
  font-family: Georgia, "Times New Roman", serif;
  margin-bottom: 10rpx;
}
.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
}
.title {
  flex: 1;
  font-size: 44rpx;
  line-height: 1.35;
}
.small-seal {
  flex: none;
  width: 48rpx;
  height: 60rpx;
  border: 2rpx solid #c45450;
  border-radius: 8rpx;
  color: #c45450;
  text-align: center;
  line-height: 58rpx;
  font-family: "Songti SC", "STSong", serif;
  font-size: 26rpx;
}
.sub {
  margin-top: 16rpx;
  color: #6f7b68;
  font-size: 28rpx;
  line-height: 1.7;
}
.sku-panel,
.detail,
.care-panel {
  margin-top: 28rpx;
  padding: 28rpx;
}
.panel-head {
  position: relative;
  z-index: 1;
  margin-bottom: 18rpx;
}
.panel-title {
  display: block;
  font-size: 34rpx;
}
.panel-sub {
  display: block;
  margin-top: 6rpx;
  color: #6f7b68;
  font-size: 24rpx;
}
.empty {
  position: relative;
  z-index: 1;
  color: #6f7b68;
  font-size: 26rpx;
}
.sku {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx;
  border: 1rpx solid rgba(111, 159, 88, 0.24);
  border-radius: 16rpx;
  margin-bottom: 14rpx;
  background: rgba(255, 255, 255, 0.64);
}
.sku.active {
  border-color: rgba(111, 159, 88, 0.62);
  background: linear-gradient(135deg, rgba(111, 159, 88, 0.13), rgba(207, 226, 200, 0.2));
}
.sku.disabled {
  opacity: 0.45;
}
.sku-name,
.price {
  display: block;
  font-size: 28rpx;
}
.sku-stock {
  display: block;
  color: #6f7b68;
  font-size: 24rpx;
  margin-top: 6rpx;
}
.detail {
  color: #5f665d;
  font-size: 28rpx;
  line-height: 1.7;
}
.care-block {
  position: relative;
  z-index: 1;
  margin-bottom: 22rpx;
}
.care-block:last-child {
  margin-bottom: 0;
}
.care-text {
  display: block;
  margin-top: 12rpx;
  color: #51664b;
  font-size: 27rpx;
  line-height: 1.75;
}
.actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-top: 32rpx;
}
</style>
