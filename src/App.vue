<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { captureInviterFromLaunch } from '@/utils/invite'
onLaunch((options) => {
  // 小程序启动时解析邀请参数（inviterUserId）
  // 注意：真机/分享/扫码进入时也可能走 onShow，这里两处都处理一次
  captureInviterFromLaunch(options as any)
})
onShow((options) => {
  captureInviterFromLaunch(options as any)
})
onHide(() => {
  // noop
})
</script>
<style>
page {
  min-height: 100%;
  background: #f4f9f0;
  color: #2d2d2d;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif;
}

view,
text,
input,
textarea,
button {
  box-sizing: border-box;
}

button {
  margin: 0;
  border-radius: 16rpx;
  font-weight: 400;
}

button::after {
  border: none;
}

.page {
  position: relative;
  min-height: 100vh;
  padding: 32rpx;
  background:
    radial-gradient(circle at 12% 4%, rgba(255, 255, 255, 0.9), transparent 30%),
    radial-gradient(circle at 88% 8%, rgba(176, 219, 165, 0.42), transparent 32%),
    radial-gradient(circle at 16% 78%, rgba(227, 241, 220, 0.72), transparent 30%),
    linear-gradient(180deg, #f4f9f0 0%, #fbfdf8 54%, #fdfdfb 100%);
  overflow: hidden;
}

.page::before {
  content: "";
  position: absolute;
  top: 108rpx;
  right: -210rpx;
  width: 860rpx;
  height: 1180rpx;
  border-radius: 52% 48% 54% 46%;
  background:
    linear-gradient(118deg, transparent 0%, rgba(255, 255, 255, 0.18) 23%, rgba(115, 174, 96, 0.2) 39%, rgba(219, 239, 211, 0.38) 53%, rgba(255, 255, 255, 0.22) 66%, transparent 100%),
    linear-gradient(132deg, transparent 0%, rgba(255, 255, 255, 0.32) 44%, rgba(95, 143, 75, 0.08) 54%, transparent 100%);
  filter: blur(1rpx);
  transform: rotate(28deg);
  pointer-events: none;
}

.page::after {
  content: "";
  position: absolute;
  left: -120rpx;
  bottom: 28rpx;
  width: 760rpx;
  height: 260rpx;
  border-bottom: 4rpx solid rgba(79, 123, 66, 0.1);
  border-radius: 50%;
  transform: rotate(-7deg);
  pointer-events: none;
}

.brand-nav {
  position: relative;
  z-index: 2;
  height: 92rpx;
  margin-bottom: 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand-nav-logo {
  width: 360rpx;
  height: 52rpx;
}

.brand-nav-spacer {
  width: 80rpx;
  height: 56rpx;
}

.ink-page-content {
  position: relative;
  z-index: 1;
}

.ink-page-content::before {
  content: "";
  position: absolute;
  left: -72rpx;
  right: -72rpx;
  top: 420rpx;
  height: 260rpx;
  background:
    radial-gradient(ellipse at 28% 86%, rgba(79, 123, 66, 0.1), transparent 42%),
    radial-gradient(ellipse at 72% 72%, rgba(111, 159, 88, 0.08), transparent 46%);
  opacity: 0.82;
  pointer-events: none;
}

.ink-card {
  position: relative;
  background: rgba(255, 255, 255, 0.92);
  border: 1rpx solid rgba(207, 226, 200, 0.9);
  border-radius: 24rpx;
  box-shadow: 0 6rpx 20rpx rgba(79, 123, 66, 0.09);
  overflow: hidden;
}

.ink-card::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 16% 12%, rgba(147, 188, 128, 0.12), transparent 30%),
    linear-gradient(135deg, rgba(247, 248, 250, 0.36), transparent 46%);
  pointer-events: none;
}

.ink-title {
  color: #2d2d2d;
  font-family: "Songti SC", "STSong", "SimSun", serif;
  font-weight: 500;
  letter-spacing: 0;
}

.ink-muted {
  color: #6f7b68;
  font-weight: 400;
}

.ink-price {
  color: #b64f45;
  font-family: Georgia, "Times New Roman", serif;
  font-weight: 600;
}

.ink-line {
  height: 1rpx;
  background: rgba(111, 159, 88, 0.24);
}

.ink-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 88rpx;
  height: 44rpx;
  padding: 0 14rpx;
  border: 2rpx solid rgba(176, 73, 63, 0.5);
  border-radius: 8rpx;
  color: #b0493f;
  font-family: "Songti SC", "STSong", "SimSun", serif;
  font-size: 22rpx;
  line-height: 40rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 0 0 1rpx rgba(176, 73, 63, 0.14);
}

.ink-btn,
.ink-btn-primary,
.ink-btn-secondary {
  position: relative;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 16rpx;
  font-size: 28rpx;
  overflow: hidden;
}

.ink-btn-primary {
  color: #fff;
  background: linear-gradient(135deg, #5f8f4b 0%, #86c166 58%, #cfe2c8 100%);
  box-shadow: 0 8rpx 22rpx rgba(111, 159, 88, 0.2);
}

.ink-btn-secondary {
  color: #2d2d2d;
  background: rgba(255, 255, 255, 0.84);
  border: 1rpx solid rgba(111, 159, 88, 0.36);
}

.ink-btn-primary:active,
.ink-btn-secondary:active,
.ink-card:active {
  transform: translateY(1rpx);
  opacity: 0.92;
}

/* 操作文字链接按压反馈 */
.refresh:active,
.delete:active,
.remove:active,
.drawer-action:active,
.search-clear:active {
  opacity: 0.55;
}

/* 步进器按钮按压 */
.step:active {
  background: rgba(111, 159, 88, 0.12);
  transform: scale(0.9);
}

/* 分类项过渡动画 */
.category-item {
  transition: all 0.2s ease;
}

.ink-search {
  display: flex;
  align-items: center;
  height: 76rpx;
  padding: 0 24rpx;
  border: 1rpx solid rgba(111, 159, 88, 0.34);
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 4rpx 16rpx rgba(111, 159, 88, 0.07);
}

.ink-search-icon {
  position: relative;
  width: 44rpx;
  height: 44rpx;
  margin-right: 14rpx;
  color: transparent;
}

.ink-search-icon::before {
  content: "";
  position: absolute;
  left: 11rpx;
  top: 5rpx;
  width: 23rpx;
  height: 31rpx;
  border: 3rpx solid #4f7b42;
  border-left-color: transparent;
  border-bottom-color: transparent;
  border-radius: 50% 50% 50% 8rpx;
  transform: rotate(-36deg);
  opacity: 0.72;
}

.ink-search-icon::after {
  content: "";
  position: absolute;
  left: 22rpx;
  top: 17rpx;
  width: 22rpx;
  height: 2rpx;
  background: rgba(79, 123, 66, 0.68);
  transform: rotate(-28deg);
}

.ink-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 240rpx;
  color: #6f7b68;
  font-size: 26rpx;
}

.ink-loading-mark {
  width: 58rpx;
  height: 58rpx;
  margin-bottom: 18rpx;
  border: 4rpx solid rgba(111, 159, 88, 0.16);
  border-top-color: #4f7b42;
  border-right-color: rgba(176, 73, 63, 0.46);
  border-radius: 50% 46% 52% 44%;
  animation: ink-rotate 1.3s ease-in-out infinite;
  box-shadow: 0 0 18rpx rgba(111, 159, 88, 0.08);
}

.ink-empty {
  min-height: 420rpx;
  padding: 56rpx 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #6f7b68;
}

.ink-empty-art {
  position: relative;
  width: 360rpx;
  height: 138rpx;
  margin-bottom: 18rpx;
}

.ink-empty-art::before,
.ink-empty-art::after {
  content: "";
  position: absolute;
  left: 24rpx;
  bottom: 28rpx;
  width: 180rpx;
  height: 86rpx;
  border-bottom: 4rpx solid rgba(79, 123, 66, 0.18);
  border-radius: 50%;
  transform: rotate(-10deg);
}

.ink-empty-art::after {
  left: 126rpx;
  bottom: 22rpx;
  width: 210rpx;
  height: 112rpx;
  border-bottom-color: rgba(111, 159, 88, 0.16);
  transform: rotate(8deg);
}

.ink-empty-title {
  color: #2d2d2d;
  font-family: "Songti SC", "STSong", "SimSun", serif;
  font-size: 42rpx;
  letter-spacing: 6rpx;
}

.ink-empty-sub {
  margin-top: 12rpx;
  color: #6f7b68;
  font-size: 26rpx;
}

@keyframes ink-rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
