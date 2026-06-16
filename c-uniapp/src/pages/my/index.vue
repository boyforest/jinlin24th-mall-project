<template>
  <view class="page">
    <view class="ink-page-content">
      <view class="profile-hero ink-card">
        <!-- 头像：已登录时可点击更换，显示真实头像或印章占位 -->
        <button
          v-if="profile"
          class="avatar-btn"
          open-type="chooseAvatar"
          @chooseavatar="onChooseAvatar"
        >
          <image
            v-if="profile.avatar"
            class="avatar-img"
            :src="profile.avatar"
            mode="aspectFill"
          />
          <view v-else class="avatar-seal">{{ profile?.nickname?.slice(0, 1) || '养' }}</view>
        </button>
        <view v-else class="avatar-seal">{{ '养' }}</view>

        <view class="profile-copy">
          <!-- 昵称：已登录时可点击编辑，使用微信原生 nickname 输入 -->
          <view v-if="profile" class="nickname-row">
            <input
              class="profile-name ink-title"
              type="nickname"
              :value="profile.nickname || ''"
              placeholder="点击设置昵称"
              :disabled="nicknameSaving"
              @blur="onNicknameBlur"
            />
            <text v-if="nicknameSaving" class="saving-hint">保存中...</text>
          </view>
          <text v-else class="profile-name ink-title">未登录</text>
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
import { computed, ref } from 'vue'
import { onShareAppMessage } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { API_BASE_URL } from '@/config/app'

const auth = useAuthStore()
const profile = computed(() => auth.profile)
const nicknameSaving = ref(false)

// 用户选择微信头像：先上传到后端获取永久 URL，再保存到用户资料
async function onChooseAvatar(e: any) {
  console.log('[Profile] chooseAvatar 触发', e.detail)
  const tempPath = e.detail?.avatarUrl
  if (!tempPath) {
    console.log('[Profile] 未获取到头像临时路径')
    return
  }

  uni.showLoading({ title: '上传中...' })
  try {
    console.log('[Profile] 开始上传头像文件到后端...', { tempPath, apiUrl: API_BASE_URL + '/user/upload/image' })
    // 1) 上传临时文件到后端
    const uploadRes = await uni.uploadFile({
      url: API_BASE_URL + '/user/upload/image',
      filePath: tempPath,
      name: 'file',
      header: { Authorization: `Bearer ${auth.token}` },
    })
    console.log('[Profile] 上传响应：', uploadRes.data)
    const body = JSON.parse(uploadRes.data)
    if (body.code !== 0) throw new Error(body.message || '上传失败')
    const avatarUrl = body.data?.url
    if (!avatarUrl) throw new Error('未获取到图片地址')
    console.log('[Profile] 头像永久 URL：', avatarUrl)

    // 2) 保存到用户资料
    console.log('[Profile] 调用 saveProfile 保存头像...')
    await auth.saveProfile(undefined, avatarUrl)
    console.log('[Profile] 头像保存完成，当前 profile：', auth.profile)
    uni.hideLoading()
    uni.showToast({ title: '头像已更新', icon: 'success' })
  } catch (e: any) {
    console.error('[Profile] 头像上传失败：', e)
    uni.hideLoading()
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' })
  }
}

// 用户填写昵称后失焦保存
async function onNicknameBlur(e: any) {
  const nickname = e.detail?.value?.trim()
  if (!nickname || nickname === profile.value?.nickname) return
  nicknameSaving.value = true
  try {
    await auth.saveProfile(nickname, undefined)
    uni.showToast({ title: '昵称已更新', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '保存失败', icon: 'none' })
  } finally {
    nicknameSaving.value = false
  }
}

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
  flex: none;
  animation: seal-ink 3s ease-in-out infinite;
}
.avatar-btn {
  position: relative;
  z-index: 1;
  width: 112rpx;
  height: 112rpx;
  padding: 0;
  margin: 0 26rpx 0 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  flex: none;
  overflow: visible;
  line-height: 1;
}
.avatar-btn::after {
  border: none;
}
.avatar-img {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  border: 4rpx double rgba(196, 84, 80, 0.62);
}
.nickname-row {
  display: flex;
  align-items: center;
}
.saving-hint {
  flex: none;
  margin-left: 14rpx;
  color: #6f7b68;
  font-size: 22rpx;
}
@keyframes seal-ink {
  0%, 100% { border-color: rgba(196, 84, 80, 0.5); box-shadow: 0 0 0 0 rgba(196, 84, 80, 0); }
  50%      { border-color: rgba(196, 84, 80, 0.72); box-shadow: 0 0 16rpx rgba(196, 84, 80, 0.08); }
}
.profile-copy {
  position: relative;
  z-index: 1;
  flex: 1;
}
.profile-name {
  display: block;
  font-size: 42rpx;
  flex: 1;
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
  animation: card-rise 0.45s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

@keyframes card-rise {
  from { opacity: 0; transform: translateY(16rpx); }
  to   { opacity: 1; transform: translateY(0); }
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
  transition: all 0.22s ease;
}
.logout-btn:active {
  background: rgba(196, 84, 80, 0.06);
  border-color: rgba(196, 84, 80, 0.42);
}
</style>
