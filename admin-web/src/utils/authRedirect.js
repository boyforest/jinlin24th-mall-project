/**
 * 401 是否需要“整页跳回登录页”的判定。
 * <p>
 * 抽成纯函数便于用 node --test 覆盖。这里的坑：登录/改密接口在密码错误时也返回 401，
 * 如果照样整页跳转 /login，页面会立刻刷新并把紧接着要弹出的错误提示冲掉，
 * 用户只会看到表单被重置、没有任何解释（原 bug）。
 */

/** 凭据类接口：它们的 401 表示“账号密码/旧密码错误”，不是登录态失效。 */
const CREDENTIAL_REQUEST_PATHS = ['/admin/login', '/admin/password']

/**
 * 判断某次 401 是否应该跳转登录页。
 *
 * @param {{ status?: number, url?: string, pathname?: string }} context
 *        status: HTTP 状态码；url: 出错请求的地址；pathname: 当前页面路径
 * @returns {boolean} true 表示需要清理登录态并整页跳转 /login
 */
export function shouldRedirectToLogin({ status, url, pathname } = {}) {
  if (status !== 401) {
    return false
  }
  const requestUrl = url || ''
  // 账号密码错误 / 旧密码错误：留在当前页面显示提示，绝不跳转
  if (CREDENTIAL_REQUEST_PATHS.some(path => requestUrl.includes(path))) {
    return false
  }
  // 已经停在登录页：再跳一次只会把提示刷掉，形成死循环观感
  if (pathname === '/login') {
    return false
  }
  return true
}
