import request from '../utils/request'

/**
 * 管理员登录。
 */
export function login(data) {
  return request.post('/admin/login', data)
}

/**
 * 管理员修改密码（首次强制改密 / 后台自主改密）。
 * 成功时返回新 token 和权限信息。
 */
export function changePassword(data) {
  return request.put('/admin/password', data)
}

export function listAdminOptions(params) {
  return request.get('/admin/admins/options', { params })
}
