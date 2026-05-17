<template>
  <view class="page">
    <view class="ink-page-content">
      <view class="page-head">
        <text class="kicker">CONFIRM</text>
        <text class="title-main ink-title">确认订单</text>
      </view>

      <view class="card ink-card">
        <view class="title ink-title">收货信息</view>
        <input v-model="form.receiverName" class="input" placeholder="收货人" />
        <input v-model="form.receiverPhone" class="input" placeholder="手机号" type="number" />
        <textarea v-model="form.receiverAddress" class="textarea" placeholder="详细地址" />
        <textarea v-model="form.remark" class="textarea" placeholder="备注（选填）" />
      </view>

      <view class="card ink-card">
        <view class="title ink-title">商品</view>
        <view class="row">
          <text>SKU ID</text>
          <text>{{ skuId || '-' }}</text>
        </view>
        <view class="ink-line"></view>
        <view class="row">
          <text>数量</text>
          <view class="stepper">
            <text class="step" @click="changeQty(-1)">-</text>
            <text class="qty">{{ quantity }}</text>
            <text class="step" @click="changeQty(1)">+</text>
          </view>
        </view>
      </view>

      <view class="card ink-card">
        <view class="title-row">
          <view>
            <text class="title ink-title">推荐官</text>
            <text class="hint">确认本单服务归属</text>
          </view>
          <text v-if="recommender" class="status-pill">已绑定</text>
          <text v-else class="status-pill muted">未绑定</text>
        </view>

        <view v-if="recommender" class="recommender-box">
          <image v-if="recommender.avatar" class="avatar" :src="recommender.avatar" mode="aspectFill" />
          <view class="recommender-info">
            <text class="name">{{ recommender.nickname || `推荐官 ${recommender.id}` }}</text>
            <text class="desc">本单将由该推荐官提供服务，支付后按规则生成佣金。</text>
          </view>
        </view>

        <view v-else class="bind-box">
          <input v-model="recommenderInput" class="input" placeholder="填写推荐官ID（选填）" type="number" />
          <button class="bind-btn" :loading="binding" @click="bind">绑定推荐官</button>
          <text class="desc">没有推荐官也可以继续下单，后续可通过分享链接或推荐官ID绑定。</text>
        </view>
      </view>

      <button class="ink-btn-primary" :loading="submitting" @click="submit">提交并支付</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { createOrder } from '@/api/order'
import { createOrderPayment, requestMiniAppPayment } from '@/api/payment'
import { bindRecommender, getRecommender, type AppUserVO } from '@/api/user'

const productId = ref<number>(0)
const skuId = ref<number>(0)
const quantity = ref(1)
const submitting = ref(false)
const binding = ref(false)
const recommender = ref<AppUserVO | null>(null)
const recommenderInput = ref('')
const form = reactive({
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  remark: '',
})

function changeQty(delta: number) {
  quantity.value = Math.max(1, quantity.value + delta)
}

async function loadRecommender() {
  try {
    recommender.value = await getRecommender()
  } catch {
    recommender.value = null
  }
}

async function bind() {
  const id = Number(recommenderInput.value)
  if (!Number.isFinite(id) || id <= 0) {
    uni.showToast({ title: '请填写有效推荐官ID', icon: 'none' })
    return
  }
  binding.value = true
  try {
    recommender.value = await bindRecommender(id)
    recommenderInput.value = ''
    uni.showToast({ title: '推荐官已绑定', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '绑定失败', icon: 'none' })
  } finally {
    binding.value = false
  }
}

async function submit() {
  if (!skuId.value) {
    uni.showToast({ title: '缺少 SKU', icon: 'none' })
    return
  }
  if (!form.receiverName || !form.receiverPhone || !form.receiverAddress) {
    uni.showToast({ title: '请填写收货信息', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const order = await createOrder({
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone,
      receiverAddress: form.receiverAddress,
      remark: form.remark,
      items: [{ skuId: skuId.value, quantity: quantity.value }],
    })
    try {
      const payParams = await createOrderPayment(order.id)
      await requestMiniAppPayment(payParams)
      uni.showToast({ title: '支付完成', icon: 'success' })
    } catch (payError: any) {
      const message = String(payError?.errMsg || payError?.message || '')
      const isCancel = message.includes('cancel')
      uni.showToast({ title: isCancel ? '支付已取消' : message || '支付未完成', icon: 'none' })
    }
    uni.redirectTo({ url: `/pages/order-detail/index?id=${order.id}` })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad((options) => {
  productId.value = Number((options as any)?.productId || 0)
  skuId.value = Number((options as any)?.skuId || 0)
  loadRecommender()
})
</script>

<style scoped>
.page-head {
  margin-bottom: 28rpx;
}
.kicker {
  display: block;
  color: #d4af37;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 20rpx;
  margin-bottom: 6rpx;
}
.title-main {
  display: block;
  font-size: 40rpx;
}
.card {
  padding: 28rpx;
  margin-bottom: 24rpx;
}
.title {
  font-size: 32rpx;
  margin-bottom: 18rpx;
}
.title-row {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  align-items: flex-start;
  margin-bottom: 18rpx;
}
.title-row .title {
  display: block;
  margin-bottom: 6rpx;
}
.hint,
.desc {
  display: block;
  color: #6d7c67;
  font-size: 24rpx;
  line-height: 1.5;
}
.status-pill {
  flex: 0 0 auto;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  color: #2c6b46;
  background: rgba(134, 193, 102, 0.16);
  border: 1rpx solid rgba(83, 142, 89, 0.18);
  font-size: 22rpx;
  font-weight: 700;
}
.status-pill.muted {
  color: #8a7651;
  background: rgba(212, 175, 55, 0.12);
  border-color: rgba(212, 175, 55, 0.18);
}
.input,
.textarea {
  position: relative;
  z-index: 1;
  width: 100%;
  box-sizing: border-box;
  background: rgba(247, 248, 250, 0.82);
  border: 1rpx solid rgba(134, 193, 102, 0.2);
  border-radius: 16rpx;
  padding: 18rpx;
  margin-bottom: 14rpx;
  font-size: 28rpx;
}
.textarea {
  min-height: 120rpx;
}
.recommender-box {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 20rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.66);
  border: 1rpx solid rgba(134, 193, 102, 0.22);
}
.avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: rgba(134, 193, 102, 0.16);
}
.recommender-info {
  min-width: 0;
  flex: 1;
}
.name {
  display: block;
  color: #254b32;
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 4rpx;
}
.bind-box {
  position: relative;
  z-index: 1;
}
.bind-btn {
  width: 100%;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 14rpx;
  margin: 6rpx 0 14rpx;
  color: #fff;
  background: linear-gradient(135deg, #3f7f54, #6eaa62);
  font-size: 26rpx;
}
.bind-btn::after {
  border: 0;
}
.row {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18rpx 0;
  color: #2d2d2d;
}
.stepper {
  display: flex;
  align-items: center;
  border: 1rpx solid rgba(134, 193, 102, 0.28);
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.72);
}
.step,
.qty {
  min-width: 56rpx;
  text-align: center;
  line-height: 52rpx;
}
</style>
