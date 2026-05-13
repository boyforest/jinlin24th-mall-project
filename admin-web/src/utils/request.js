import axios from 'axios'
import { App } from 'antd'
import { clearAuth, getToken } from './auth'

let messageApi = null

/**
 * 在根组件中注入 Ant Design message 实例，避免在非组件文件中直接调用 hooks。
 */
export function bindMessageApi(api) {
  messageApi = api
}

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

/**
 * 请求拦截器：自动携带后台 token。
 */
request.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 响应拦截器：保持后端 Result.data 返回值不变，同时统一处理错误提示。
 */
request.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const data = response.data
    if (data && data.code !== 0 && data.code !== 200) {
      const error = new Error(data.message || `业务错误 ${data.code}`)
      error.code = data.code
      throw error
    }
    return data?.data
  },
  error => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '请求失败'
    if (status === 401) {
      clearAuth()
      window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
    }
    messageApi?.error(message)
    return Promise.reject(new Error(message))
  }
)

/**
 * Ant Design App hook 桥接组件，挂在路由根部用于初始化全局 message。
 */
export function RequestMessageBinder() {
  const { message } = App.useApp()
  bindMessageApi(message)
  return null
}

export default request
