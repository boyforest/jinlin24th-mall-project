import request from '../utils/request'

/**
 * 管理员登录。
 */
export function login(data) {
  return request.post('/admin/login', data)
}
