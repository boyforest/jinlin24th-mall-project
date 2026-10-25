export const APP_NAME = '金霖二十四养'

// 后端 API 地址：
// - 小程序上线/体验版：必须通过 VITE_API_BASE_URL 指向 HTTPS 线上域名
// - 本地联调如需改地址，请通过 .env.development 配置 VITE_API_BASE_URL
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'https://api.jinlin24th.cn').replace(/\/+$/, '')

export const STORAGE_KEYS = {
  token: 'jl_user_token',
  userId: 'jl_user_id',
  inviterUserId: 'jl_inviter_user_id',
} as const

export const PAGE_URLS = {
  login: '/pages/login/index',
  home: '/pages/home/index',
  goods: '/pages/goods/index',
  coffee: '/pages/coffee/index',
  cart: '/pages/cart/index',
  orders: '/pages/orders/index',
  my: '/pages/my/index',
} as const
