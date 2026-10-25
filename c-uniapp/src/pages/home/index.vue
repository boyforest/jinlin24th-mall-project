<template>
  <view class="page home-page" :class="{ 'page-hidden': !visible }">
    <view class="ink-page-content">
      <SolarTermHero :now="localNow" :active="visible" />
      <view class="business-entries">
        <button class="business-entry goods-entry" @click="go('goods')"><text class="entry-mark">养</text><text class="entry-title">商品选购</text><text class="entry-sub">顺时好物 · 日常滋养 ↗</text></button>
        <button class="business-entry coffee-entry" @click="go('coffee')"><text class="entry-mark">饮</text><text class="entry-title">咖啡点单</text><text class="entry-sub">一杯闲时 · 点单筹备中 ↗</text></button>
      </view>
      <view class="care-note"><text>顺时养护</text><text>循节气而食，让日常慢下来。</text></view>
      <view v-if="notices.length" class="notice-strip" @click="openActivity(notices[0])">
        <text class="notice-mark">告</text>
        <text class="notice-text">{{ notices[0].title }}</text>
      </view>

      <view v-if="banners.length" class="activity-card" @click="openActivity(banners[0])">
        <image v-if="banners[0].imageUrl" class="activity-image" :src="banners[0].imageUrl" mode="aspectFill" />
        <view class="activity-copy"><text class="activity-title ink-title">{{ banners[0].title }}</text><text v-if="banners[0].subtitle" class="activity-sub">{{ banners[0].subtitle }}</text></view>
      </view>
      <view v-if="recommendations.length" class="recommendations">
        <text class="recommend-heading">推荐养护</text>
        <view class="recommend-grid">
          <view v-for="activity in recommendations" :key="activity.id" class="recommend-item" @click="openActivity(activity)">
            <image v-if="activity.imageUrl" class="recommend-image" :src="activity.imageUrl" mode="aspectFill" />
            <text class="recommend-title">{{ activity.title }}</text>
            <text v-if="activity.subtitle" class="recommend-reason">{{ activity.subtitle }}</text>
          </view>
        </view>
      </view>

    </view>
    <view v-if="popupVisible && loginPopup" class="popup-mask" @click="popupVisible = false">
      <view class="popup-card ink-card" @click.stop>
        <view class="popup-close" @click="popupVisible = false">✕</view>
        <image
          v-if="loginPopup.imageUrl"
          class="popup-image"
          :src="loginPopup.imageUrl"
          mode="widthFix"
        />
        <view class="popup-title ink-title">{{ loginPopup.title }}</view>
        <text v-if="loginPopup.subtitle" class="popup-sub">{{ loginPopup.subtitle }}</text>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onHide, onPullDownRefresh } from '@dcloudio/uni-app'
import SolarTermHero from '@/components/SolarTermHero.vue'
import { listMarketingActivities, type MarketingActivityVO } from '@/api/marketing'
import { getLocalDateTime } from '@/utils/solarTerms'
const visible = ref(true)
const localNow = ref(getLocalDateTime())
const banners = ref<MarketingActivityVO[]>([])
const recommendations = ref<MarketingActivityVO[]>([])
const notices = ref<MarketingActivityVO[]>([])
const loginPopup = ref<MarketingActivityVO | null>(null)
const popupVisible = ref(false)
function go(business: string) { uni.navigateTo({ url: `/pages/${business}/index` }) }
async function loadActivities() {
  // 各位置独立更新；接口已按有效期与 sort DESC / id DESC 排序。
  await Promise.allSettled([
    listMarketingActivities('home_banner').then(data => { banners.value = (data || []).slice(0, 1) }).catch(() => { banners.value = [] }),
    listMarketingActivities('home_recommend').then(data => { recommendations.value = (data || []).slice(0, 6) }).catch(() => { recommendations.value = [] }),
    listMarketingActivities('home_notice').then(data => { notices.value = data || [] }).catch(() => { notices.value = [] }),
    listMarketingActivities('login_popup').then(data => { loginPopup.value = data?.[0] || null }).catch(() => { loginPopup.value = null }),
  ])
}

function openActivity(activity: MarketingActivityVO) {
  if (activity.linkType === 'product' && activity.linkValue) {
    uni.navigateTo({ url: `/pages/product-detail/index?id=${activity.linkValue}` })
    return
  }
  if (activity.linkType === 'category' && activity.linkValue) {
    uni.navigateTo({ url: `/pages/goods/index?categoryId=${encodeURIComponent(activity.linkValue)}` })
    return
  }
  if (activity.linkType === 'page' && activity.linkValue) {
    const url = activity.linkValue
    const path = url.split('?')[0]
    if (['/pages/home/index', '/pages/cart/index', '/pages/orders/index', '/pages/my/index'].includes(path)) uni.switchTab({ url: path })
    else uni.navigateTo({ url })
  }
}


onShow(() => {
  visible.value = true
  localNow.value = getLocalDateTime()
  loadActivities().then(() => {
    if (loginPopup.value && !uni.getStorageSync('popup_login_shown')) {
      popupVisible.value = true
      uni.setStorageSync('popup_login_shown', '1')
    }
  })
})
onHide(() => { visible.value = false })
onPullDownRefresh(async () => { await loadActivities(); uni.stopPullDownRefresh() })
</script>
<style scoped>
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
.activity-card {
  margin-bottom: 26rpx;
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
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif;
  font-size: 34rpx;
  font-weight: 500;
}
.activity-sub {
  margin-top: 8rpx;
  color: #6d7c67;
  font-size: 24rpx;
}
.recommend-heading { display: block; font-size: 30rpx; color: #314d35; margin: 28rpx 0 20rpx; }
.recommend-grid { display: flex; flex-wrap: wrap; margin: 0 -10rpx; }
.recommend-item { width: calc(50% - 20rpx); margin: 0 10rpx 28rpx; display: flex; flex-direction: column; }
.recommend-image { width: 100%; height: 240rpx; border-radius: 14rpx; background: #e6eddc; }
.recommend-title { color: #2e382c; font-size: 28rpx; margin-top: 14rpx; line-height: 1.5; }
.recommend-reason { color: #65755d; font-size: 23rpx; margin-top: 6rpx; line-height: 1.6; }
/* 登录弹窗 */
.popup-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: popup-fade 0.28s ease both;
}
@keyframes popup-fade {
  from { opacity: 0; }
  to   { opacity: 1; }
}
.popup-card {
  position: relative;
  width: 600rpx;
  padding: 48rpx 36rpx 36rpx;
  animation: popup-scale 0.36s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}
@keyframes popup-scale {
  from { opacity: 0; transform: scale(0.9) translateY(20rpx); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}
.popup-close {
  position: absolute;
  top: 16rpx;
  right: 20rpx;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: #9aac94;
  border-radius: 50%;
}
.popup-image {
  width: 100%;
  display: block;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
}
.popup-title {
  font-size: 34rpx;
  margin-bottom: 8rpx;
}
.popup-sub {
  font-size: 26rpx;
  color: #6f7b68;
  line-height: 1.5;
}

.home-page { min-height: calc(100vh - var(--window-top, 0px) - var(--window-bottom, 0px)); padding: 20rpx 38rpx 60rpx; }
.business-entries { display: flex; padding: 25rpx 0; border-top: 1rpx solid #dce5d4; border-bottom: 1rpx solid #dce5d4; }
.business-entry { flex: 1; display: flex; flex-direction: column; align-items: flex-start; padding: 0 24rpx; background: transparent; border-radius: 0; line-height: 1.5; text-align: left; }
.business-entry::after { border: 0; }
.business-entry + .business-entry { border-left: 1rpx solid #dce5d4; }
.entry-mark { color: #637f51; font-size: 28rpx; }
.entry-title { color: #2e382c; font-size: 32rpx; font-weight: 600; margin: 6rpx 0; }
.entry-sub { color: #65755d; font-size: 20rpx; white-space: nowrap; }
.care-note { display: flex; justify-content: space-between; gap: 10rpx; padding: 30rpx 4rpx; font-size: 23rpx; color: #65755d; }
@media (prefers-reduced-motion: reduce) { view, text, image, button, scroll-view, view::before, view::after, text::before, text::after, button::before, button::after { animation: none !important; transition: none !important; } }
</style>
