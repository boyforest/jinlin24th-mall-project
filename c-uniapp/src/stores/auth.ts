import { defineStore } from 'pinia'
import { login, me, updateProfile, type AppUserVO } from '@/api/user'
import { clearInviterUserId, getInviterUserId, getToken, getUserId, setToken, setUserId } from '@/utils/storage'
import { STORAGE_KEYS } from '@/config/app'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    userId: getUserId() || 0,
    profile: null as AppUserVO | null,
    loading: false,
  }),
  actions: {
    async loginWithWeixinProfile(payload: { nickname?: string; avatarUrl?: string } = {}) {
      this.loading = true
      try {
        if (import.meta.env.DEV) console.log('[Auth] 步骤1/3：调用 uni.login 获取微信 code...')
        const wxLoginRes = await uni.login({ provider: 'weixin' })
        const code = (wxLoginRes as any)?.code
        if (import.meta.env.DEV) console.log('[Auth] 步骤1/3 结果：', { hasCode: !!code })
        if (!code) throw new Error('未获取到 wx.login code')

        const inviterUserId = getInviterUserId()
        if (import.meta.env.DEV) console.log('[Auth] 步骤2/3：调用后端 /user/appUser/login...')
        const res = await login({
          code,
          nickname: payload.nickname,
          avatarUrl: payload.avatarUrl,
          inviterUserId,
        })
        if (import.meta.env.DEV) console.log('[Auth] 步骤2/3 结果：', { hasToken: !!res.token, userId: res.userId })

        this.token = res.token
        this.userId = res.userId
        setToken(res.token)
        setUserId(res.userId)
        clearInviterUserId()

        if (import.meta.env.DEV) console.log('[Auth] 步骤3/3：调用 /user/appUser/me 获取用户信息...')
        await this.refreshMe()
        if (import.meta.env.DEV) console.log('[Auth] 步骤3/3 完成')
        return res
      } catch (e: any) {
        if (import.meta.env.DEV) console.error('[Auth] 登录链路失败 - 步骤详情：', {
          message: e?.message,
          errMsg: e?.errMsg,
          statusCode: e?.statusCode,
        })
        throw e
      } finally {
        this.loading = false
      }
    },

    async refreshMe() {
      const info = await me()
      this.profile = info
      return info
    },

    async saveProfile(nickname?: string, avatar?: string) {
      const updated = await updateProfile({ nickname, avatar })
      this.profile = updated
      return updated
    },

    logout() {
      this.token = ''
      this.userId = 0
      this.profile = null
      uni.removeStorageSync(STORAGE_KEYS.token)
      uni.removeStorageSync(STORAGE_KEYS.userId)
      clearInviterUserId()
    },
  },
})
