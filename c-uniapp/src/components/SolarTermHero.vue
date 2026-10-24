<template>
  <view class="hero">
    <view class="brand-row">
      <image class="official-logo" src="/static/brand/official-logo.png" mode="widthFix" />
    </view>
    <view class="solar-term">
      <view class="term-calendar">
        <text class="term-position">{{ positionText }}</text>
        <text class="solar-date">{{ info.currentDateText }}</text>
      </view>
      <view class="term-center">
        <view class="term-character term-character-first">
          <text v-if="missingGlyphs.includes(term.chars[0])" class="term-cn">{{ term.chars[0] }}</text>
          <image v-else class="term-ink" :src="glyphSource(term.chars[0])" :aria-label="term.chars[0]" mode="aspectFit" @error="markMissing(term.chars[0])" />
        </view>
        <text class="term-en">{{ term.english }}</text>
        <view class="term-bottom">
          <view class="term-character term-character-second">
            <text v-if="missingGlyphs.includes(term.chars[1])" class="term-cn">{{ term.chars[1] }}</text>
            <image v-else class="term-ink" :src="glyphSource(term.chars[1])" :aria-label="term.chars[1]" mode="aspectFit" @error="markMissing(term.chars[1])" />
          </view>
          <text class="term-stamp">节气</text>
        </view>
      </view>
      <view class="term-copy">
        <text class="term-poem">{{ term.poem }}</text>
        <text class="term-time">{{ countdownText }}</text>
        <text class="term-poem-en">{{ term.poemEn }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { formatSolarTermCountdown, formatSolarTermPosition, getCurrentSolarTermInfo, type LocalDateTime } from '@/utils/solarTerms'

const props = defineProps<{ now: LocalDateTime }>()
const info = computed(() => getCurrentSolarTermInfo(props.now))
const term = computed(() => info.value.currentTerm)
const positionText = computed(() => formatSolarTermPosition(term.value))
const countdownText = computed(() => formatSolarTermCountdown(info.value))
const missingGlyphs = ref<string[]>([])

function glyphSource(character: string) {
  return `/static/solar-terms/ink-${character.codePointAt(0)?.toString(16)}.png`
}

function markMissing(character: string) {
  if (!missingGlyphs.value.includes(character)) missingGlyphs.value.push(character)
}
</script>

<style scoped>
.hero {
  min-height: 1248rpx;
  padding: 52rpx 34rpx 54rpx;
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
  pointer-events: none;
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
  pointer-events: none;
}
.brand-row,
.solar-term,
.term-calendar,
.term-center,
.term-copy {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.brand-row { opacity: 0.92; }
.official-logo { width: 540rpx; opacity: 0.88; }
.solar-term { margin-top: 92rpx; }
.term-calendar,
.term-copy {
  width: 100%;
  text-align: center;
  /* Quiet text areas: no animated layers or opacity changes. */
}
.term-position {
  color: #53634c;
  font-size: 25rpx;
  line-height: 36rpx;
  letter-spacing: 2rpx;
}
.solar-date {
  margin-top: 12rpx;
  color: #47513f;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 30rpx;
  line-height: 34rpx;
  letter-spacing: 4rpx;
  font-variant-numeric: tabular-nums;
}
.term-center { margin-top: 68rpx; }
.term-character {
  position: relative;
  width: 208rpx;
  height: 196rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  animation: ink-arrive 1.25s ease-out both;
}
.term-character-second { animation-delay: 0.18s; }
.term-character::before {
  content: "";
  position: absolute;
  inset: 10rpx;
  border-radius: 50%;
  background: radial-gradient(ellipse, rgba(95, 143, 75, 0.09), transparent 68%);
  animation: ink-breathe 7s ease-in-out infinite;
  pointer-events: none;
}
.term-character-second::before { animation-delay: -3.5s; }
.term-ink {
  position: relative;
  width: 208rpx;
  height: 208rpx;
}
.term-cn {
  color: #203427;
  font-family: "Huiwen Mincho Solar", "LXGW Heart Serif", "Songti SC", "STSong", serif;
  font-size: 180rpx;
  font-weight: 400;
  line-height: 196rpx;
}
.term-en {
  margin: 16rpx 0;
  color: #59644f;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 28rpx;
  line-height: 36rpx;
  letter-spacing: 2rpx;
}
.term-bottom { position: relative; }
.term-stamp {
  position: absolute;
  right: -48rpx;
  bottom: 28rpx;
  width: 40rpx;
  height: 64rpx;
  border: 2rpx solid rgba(176, 73, 63, 0.8);
  border-radius: 5rpx;
  color: #a74e42;
  font-family: "Songti SC", "STSong", serif;
  font-size: 21rpx;
  line-height: 30rpx;
  text-align: center;
}
.term-copy { margin-top: 104rpx; }
.term-poem {
  color: #384331;
  font-family: "LXGW Heart Serif", "Songti SC", "STSong", serif;
  font-size: 28rpx;
  line-height: 42rpx;
  letter-spacing: 9rpx;
}
.term-time {
  margin-top: 16rpx;
  color: #526b43;
  font-size: 25rpx;
  line-height: 36rpx;
  letter-spacing: 2rpx;
  font-variant-numeric: tabular-nums;
}
.term-poem-en {
  margin-top: 18rpx;
  color: #78816e;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 22rpx;
  line-height: 30rpx;
}
@keyframes ink-arrive {
  from { opacity: 0; transform: scale(1.035); }
  to { opacity: 1; transform: none; }
}
@keyframes ink-breathe {
  0%, 100% { opacity: 0.35; transform: scale(0.94); }
  50% { opacity: 0.8; transform: scale(1.04); }
}
@media (prefers-reduced-motion: reduce) {
  .term-character,
  .term-character::before { animation: none; }
}
</style>
