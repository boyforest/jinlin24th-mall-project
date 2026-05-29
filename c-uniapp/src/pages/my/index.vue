<template>
  <view class="page">
    <view class="ink-page-content">
      <view class="profile-hero ink-card">
        <view class="avatar-seal">{{ profile?.nickname?.slice(0, 1) || '养' }}</view>
        <view class="profile-copy">
          <text class="profile-name ink-title">{{ profile?.nickname || '未登录' }}</text>
          <text class="profile-sub">{{ profile ? '顺时而养，日日有节' : '登录后查看会员与订单' }}</text>
        </view>
      </view>

      <view class="card ink-card">
        <view class="row">
          <text class="label">登录状态</text>
          <text class="value">{{ profile ? '已登录' : '未登录' }}</text>
        </view>
        <view class="ink-line"></view>
        <view class="row">
          <text class="label">用户ID</text>
          <text class="value">{{ profile?.id ?? '-' }}</text>
        </view>
        <view class="ink-line"></view>
        <view class="row">
          <text class="label">会员等级</text>
          <text class="value">{{ profile?.memberLevelName ?? '-' }}</text>
        </view>
        <view class="ink-line"></view>
        <view class="row">
          <text class="label">积分</text>
          <text class="value ink-price">{{ profile?.points ?? 0 }}</text>
        </view>
      </view>

      <view v-if="profile" class="card ink-card">
        <view class="section-title ink-title">分销官状态</view>
        <view class="row">
          <text class="label">当前身份</text>
          <text class="value">{{ profile?.isDistributor === 1 ? '已开通分销官' : '普通用户' }}</text>
        </view>
        <view class="ink-line"></view>
        <view class="row">
          <text class="label">我的推荐官</text>
          <text class="value">{{ profile?.parentUserNickname || (profile?.parentUserId ? `推荐官 ${profile.parentUserId}` : '暂未绑定') }}</text>
        </view>
        <view class="ink-line"></view>
        <view class="row">
          <text class="label">推荐官电话</text>
          <text class="value">{{ profile?.parentUserPhone || '-' }}</text>
        </view>
        <view v-if="profile?.isDistributor === 1" class="share-box">
          <text class="share-tip">把你的专属邀请链接分享给好友，好友首次登录后会自动绑定到你名下。</text>
          <button class="ink-btn-primary share-btn" open-type="share">立即分享邀请</button>
        </view>
      </view>

      <button v-if="!profile" class="ink-btn-primary action-btn" @click="goLogin">微信登录</button>
      <template v-else>
        <button class="ink-btn-secondary action-btn" @click="refresh">刷新资料</button>
        <button class="logout-btn action-btn" @click="logout">退出登录</button>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { onShareAppMessage } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const profile = computed(() => auth.profile)

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

async function refresh() {
  try {
    await auth.refreshMe()
    uni.showToast({ title: '已刷新', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '刷新失败', icon: 'none' })
  }
}

function logout() {
  auth.logout()
  uni.showToast({ title: '已退出', icon: 'success' })
}

onShareAppMessage(() => {
  const userId = profile.value?.id
  return {
    title: '邀请你一起体验金霖二十四养',
    path: userId ? `/pages/home/index?inviterUserId=${userId}` : '/pages/home/index',
  }
})
</script>

<style scoped>
.profile-hero {
  display: flex;
  align-items: center;
  padding: 34rpx;
  margin-bottom: 28rpx;
}
.avatar-seal {
  position: relative;
  z-index: 1;
  width: 112rpx;
  height: 112rpx;
  border: 4rpx double rgba(196, 84, 80, 0.62);
  border-radius: 50%;
  color: #c45450;
  font-family: "Songti SC", "STSong", serif;
  font-size: 42rpx;
  line-height: 108rpx;
  text-align: center;
  margin-right: 26rpx;
}
.profile-copy {
  position: relative;
  z-index: 1;
  flex: 1;
}
.profile-name {
  display: block;
  font-size: 42rpx;
}
.profile-sub {
  display: block;
  margin-top: 10rpx;
  color: #6f7b68;
  font-size: 26rpx;
}
.card {
  padding: 26rpx;
  margin-bottom: 28rpx;
}
.section-title {
  position: relative;
  z-index: 1;
  display: block;
  margin-bottom: 12rpx;
  font-size: 32rpx;
}
.row {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  padding: 18rpx 0;
  gap: 20rpx;
}
.label {
  color: #6f7b68;
  flex: 0 0 auto;
}
.value {
  color: #2d2d2d;
  flex: 1;
  text-align: right;
  word-break: break-all;
}
.action-btn {
  margin-bottom: 18rpx;
}
.share-box {
  position: relative;
  z-index: 1;
  margin-top: 18rpx;
  padding-top: 18rpx;
}
.share-tip {
  display: block;
  color: #6f7b68;
  font-size: 24rpx;
  line-height: 1.6;
  margin-bottom: 16rpx;
}
.share-btn {
  width: 100%;
}
.logout-btn {
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 16rpx;
  color: #c45450;
  background: rgba(255, 255, 255, 0.8);
  border: 1rpx solid rgba(196, 84, 80, 0.26);
}
</style>
