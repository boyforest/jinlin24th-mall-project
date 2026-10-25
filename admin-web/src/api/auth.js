import request from '../utils/request'

/**
 * 管理员登录。
 * <p>
 * 带 {@code skipErrorMessage}：登录失败由登录页自己就地提示（贴近表单，可读性更好）。
 * 否则全局响应拦截器会先弹一次、登录页再弹一次，出现两个一模一样的提示——
 * 这个问题在修复"401 整页跳转冲掉提示"之前被页面刷新掩盖了。
 */
export function login(data) {
  return request.post('/admin/login', data, { skipErrorMessage: true })
}

/**
 * 管理员修改密码（首次强制改密 / 后台自主改密）。
 * 成功时返回新 token 和权限信息。
 * <p>
 * 同样由 LoginPage 就地提示，避免与全局提示重复。
 */
export function changePassword(data) {
  return request.put('/admin/password', data, { skipErrorMessage: true })
}

/**
 * 管理员退出登录：删除服务端 Redis 中的 jti，使 token 立即失效。
 * 仅清 localStorage 无法阻止已泄漏的 token 在有效期内继续使用，因此必须调用本接口。
 */
export function logout() {
  return request.post('/admin/logout')
}

export function listAdminOptions(params) {
  return request.get('/admin/admins/options', { params })
}

export const getAdminContext = () => request.get('/admin/auth/context', { skipErrorMessage: true })
