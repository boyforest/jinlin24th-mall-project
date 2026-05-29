import request from '../utils/request'

/**
 * 管理员登录。
 */
export function login(data) {
  return request.post('/admin/login', data)
}

export function listAdminOptions(params) {
  return request.get('/admin/admins/options', { params })
}
