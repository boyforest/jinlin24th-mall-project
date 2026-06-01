import { defineStore } from 'pinia'
import { login, me, type AppUserVO } from '@/api/user'
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
        const wxLoginRes = await uni.login({ provider: 'weixin' })
        const code = (wxLoginRes as any)?.code
        if (!code) throw new Error('未获取到 wx.login code')

        const inviterUserId = getInviterUserId()
        const res = await login({
          code,
          nickname: payload.nickname,
          avatarUrl: payload.avatarUrl,
          inviterUserId,
        })

        this.token = res.token
        this.userId = res.userId
        setToken(res.token)
        setUserId(res.userId)
        // 绑定只发生在首次登录；一旦登录成功就清掉缓存，避免后续误传造成困扰
        clearInviterUserId()

        await this.refreshMe()
        return res
      } finally {
        this.loading = false
      }
    },

    async refreshMe() {
      const info = await me()
      this.profile = info
      return info
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
