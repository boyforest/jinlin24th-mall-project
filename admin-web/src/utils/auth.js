export const TOKEN_KEY = 'jinlin_admin_token'
export const USERNAME_KEY = 'jinlin_admin_username'
export const ADMIN_ID_KEY = 'jinlin_admin_id'
export const REAL_NAME_KEY = 'jinlin_admin_real_name'
export const ROLES_KEY = 'jinlin_admin_roles'
export const PERMISSIONS_KEY = 'jinlin_admin_permissions'

/**
 * 获取当前管理员 token。
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

/**
 * 保存登录态。
 */
export function saveAuth(data) {
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USERNAME_KEY, data.username)
  localStorage.setItem(ADMIN_ID_KEY, data.adminId || '')
  localStorage.setItem(REAL_NAME_KEY, data.realName || '')
  localStorage.setItem(ROLES_KEY, JSON.stringify(data.roles || []))
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(data.permissions || []))
}

/**
 * 清空登录态。
 */
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
  localStorage.removeItem(ADMIN_ID_KEY)
  localStorage.removeItem(REAL_NAME_KEY)
  localStorage.removeItem(ROLES_KEY)
  localStorage.removeItem(PERMISSIONS_KEY)
}

/**
 * 获取当前管理员账号名。
 */
export function getUsername() {
  return localStorage.getItem(USERNAME_KEY) || ''
}

/**
 * 获取当前管理员真实姓名。
 */
export function getRealName() {
  return localStorage.getItem(REAL_NAME_KEY) || ''
}

/**
 * 获取当前管理员 ID。
 */
export function getAdminId() {
  const value = localStorage.getItem(ADMIN_ID_KEY)
  return value ? Number(value) : undefined
}

/**
 * 获取当前管理员角色编码。
 */
export function getRoles() {
  return JSON.parse(localStorage.getItem(ROLES_KEY) || '[]')
}

/**
 * 获取当前管理员权限编码。
 */
export function getPermissions() {
  return JSON.parse(localStorage.getItem(PERMISSIONS_KEY) || '[]')
}
