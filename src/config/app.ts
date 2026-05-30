export const APP_NAME = '金霖二十四养'

// 后端 API 地址：
// - 小程序真机/微信开发者工具：建议改成你电脑局域网 IP（不能用 localhost）
// - H5：可以用 localhost
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:7878'

export const STORAGE_KEYS = {
  token: 'jl_user_token',
  userId: 'jl_user_id',
  inviterUserId: 'jl_inviter_user_id',
} as const

export const PAGE_URLS = {
  login: '/pages/login/index',
  home: '/pages/home/index',
  cart: '/pages/cart/index',
  orders: '/pages/orders/index',
  my: '/pages/my/index',
} as const
