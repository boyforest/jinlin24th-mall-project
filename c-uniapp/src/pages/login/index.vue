<template>
  <view class="page">
    <view class="ink-page-content">
      <view class="login-visual">
        <image class="official-logo" src="/static/brand/official-logo.png" mode="widthFix" />
      </view>

      <view class="card ink-card">
        <view class="title ink-title">静候相逢</view>
        <view class="desc">登录后收藏节气养物，查看订单与会员积分。</view>

        <view class="profile-row">
          <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
            <image
              v-if="avatarPath"
              class="avatar-preview"
              :src="avatarPath"
              mode="aspectFill"
            />
            <view v-else class="avatar-placeholder">+</view>
          </button>
          <input
            class="nickname-input"
            type="nickname"
            v-model="nickname"
            placeholder="点击填写微信昵称"
            maxlength="20"
          />
        </view>

        <button class="ink-btn-primary" :loading="auth.loading" @click="doLogin">微信一键登录</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { PAGE_URLS, API_BASE_URL } from '@/config/app'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const redirect = ref('')
const nickname = ref('')
const avatarPath = ref('')

function onChooseAvatar(e: any) {
  const path = e.detail?.avatarUrl
  if (path) avatarPath.value = path
}

async function uploadAvatarAfterLogin() {
  if (!avatarPath.value || !auth.token) return
  try {
    const res = await uni.uploadFile({
      url: API_BASE_URL + '/user/upload/image',
      filePath: avatarPath.value,
      name: 'file',
      header: { Authorization: `Bearer ${auth.token}` },
    })
    const body = JSON.parse(res.data)
    if (body.code === 0 && body.data?.url) {
      await auth.saveProfile(undefined, body.data.url)
    }
  } catch (e: any) {
    console.warn('[Login] 头像上传失败（不影响登录）', e.message)
  }
}

async function doLogin() {
  try {
    await auth.loginWithWeixinProfile({
      nickname: nickname.value || undefined,
    })

    // 头像上传不阻塞跳转，后台执行
    uploadAvatarAfterLogin()

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
.profile-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 32rpx;
}
.avatar-btn {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  padding: 0;
  background: none;
  border: 2rpx dashed #cfe2c8;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar-btn::after {
  border: none;
}
.avatar-preview {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
}
.avatar-placeholder {
  font-size: 40rpx;
  color: #b8ceb1;
  line-height: 1;
}
.nickname-input {
  flex: 1;
  height: 80rpx;
  background: #f4f9f0;
  border-radius: 16rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #2d2d2d;
}
</style>
