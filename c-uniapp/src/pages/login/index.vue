<template>
  <view class="page">
    <view class="ink-page-content">
      <view class="login-visual">
        <image class="official-logo" src="/static/brand/official-logo.png" mode="widthFix" />
      </view>

      <view class="card ink-card">
        <view class="title ink-title">静候相逢</view>
        <view class="desc">登录后收藏节气养物，查看订单与会员积分。</view>
        <button class="ink-btn-primary" :loading="auth.loading" @click="doLogin">微信一键登录</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { PAGE_URLS } from '@/config/app'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const redirect = ref('')

async function doLogin() {
  try {
    await auth.loginWithWeixinProfile()
    uni.showToast({ title: '登录成功', icon: 'success' })
    if (redirect.value) {
      if (isTabBarPage(redirect.value)) {
        uni.switchTab({ url: redirect.value })
      } else {
        uni.redirectTo({ url: redirect.value })
      }
    } else {
      uni.navigateBack()
    }
  } catch (e: any) {
    // 真机调试：打开控制台查看完整错误
    console.error('[Login] 登录失败详情', {
      message: e?.message,
      errMsg: e?.errMsg,
      statusCode: e?.statusCode,
      raw: JSON.stringify(e),
    })
    uni.showToast({ title: e?.message || '登录失败', icon: 'none', duration: 3000 })
  }
}

onLoad((options) => {
  redirect.value = decodeURIComponent(String((options as any)?.redirect || ''))
})

function isTabBarPage(url: string) {
  const path = url.split('?')[0]
  return [PAGE_URLS.home, PAGE_URLS.cart, PAGE_URLS.orders, PAGE_URLS.my].includes(path as any)
}
</script>

<style scoped>
.login-visual {
  min-height: 520rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
}
.login-visual::before {
  content: "";
  position: absolute;
  width: 520rpx;
  height: 520rpx;
  border-radius: 46% 54% 48% 52%;
  background: radial-gradient(circle at 40% 38%, rgba(176, 219, 165, 0.26), transparent 62%);
  filter: blur(20rpx);
  pointer-events: none;
  animation: logo-aura 5s ease-in-out infinite;
}
@keyframes logo-aura {
  0%, 100% { transform: scale(1) rotate(0deg); opacity: 0.7; }
  50%      { transform: scale(1.08) rotate(3deg); opacity: 1; }
}
.official-logo {
  width: 560rpx;
  position: relative;
  z-index: 1;
  animation: logo-breathe 5s ease-in-out infinite;
}
@keyframes logo-breathe {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.03); }
}
.card {
  padding: 36rpx;
  box-shadow: 0 8rpx 28rpx rgba(79, 123, 66, 0.12);
}
.title {
  font-size: 40rpx;
  margin-bottom: 16rpx;
}
.desc {
  font-size: 28rpx;
  color: #6f7b68;
  margin-bottom: 32rpx;
  line-height: 1.65;
}
</style>
