/**
 * 登录跳转地址白名单校验（纯函数，便于单元测试）。
 * <p>
 * 背景：`/login?redirect=xxx` 中的 xxx 完全由攻击者可控。react-router 7.15.0 属于
 * GHSA-wrjc-x8rr-h8h6 影响范围，已确认可被 `/\evil.com` 这类地址绕过：
 * `new URL('/\\evil.com', 'https://admin.jinlin24th.cn').href === 'https://evil.com/'`，
 * 且 react-router 在 pushState 因跨域抛错时会回退到真实的 window.location.assign，
 * 最终把管理员带到钓鱼站点。
 * <p>
 * 策略：只放行“同源相对路径”，其余一律回退到 fallback。
 * 宁可误杀（合法的相对路径被拦下，退化成回首页），也不放过（开放重定向）。
 */

/** 兜底路径：任何情况下都不能把管理员留在一个非法地址上。 */
const DEFAULT_FALLBACK = '/dashboard'

/** 控制字符（含 DEL/C1）：URL 中不允许出现，出现即为畸形输入。 */
const CONTROL_CHARS = /[\u0000-\u001F\u007F-\u009F]/

/** 百分号编码的 `/`（%2f）与 `\`（%5c）：部分服务端会先解码再规范化，一并拒绝。 */
const ENCODED_SEPARATOR = /%(2f|5c)/i

/**
 * 内部校验：合法返回原值，不合法返回空串。
 *
 * @param {unknown} value 待校验的值
 * @returns {string} 合法的同源相对路径，或 ''
 */
function sanitize(value) {
  // 非字符串（null/undefined/数字等）
  if (typeof value !== 'string' || value === '') {
    return ''
  }
  // 首尾空白/纯空白：不做 trim 容错，直接拒绝，避免 " /x" 之类的绕过手法
  if (value !== value.trim()) {
    return ''
  }
  // 控制字符
  if (CONTROL_CHARS.test(value)) {
    return ''
  }
  // 必须是同源相对路径：以 / 开头（这一条同时挡掉 http:/https:/javascript:/data: 等一切带协议的绝对地址）
  if (!value.startsWith('/')) {
    return ''
  }
  // 协议相对地址 //evil.com
  if (value.startsWith('//')) {
    return ''
  }
  // 反斜杠：浏览器在特殊协议下会把 \ 规范化为 /，这正是 GHSA-wrjc-x8rr-h8h6 的利用点
  if (value.includes('\\')) {
    return ''
  }
  // 任意位置的 //（/a//b、/?next=//evil.com）
  if (value.includes('//')) {
    return ''
  }
  // 编码后的分隔符
  if (ENCODED_SEPARATOR.test(value)) {
    return ''
  }
  return value
}

/**
 * 校验并返回安全的登录后跳转路径。
 *
 * @param {unknown} raw 原始值（通常来自 searchParams.get('redirect')）
 * @param {string} [fallback] 校验不通过时的兜底路径（同样会被校验，避免兜底值本身被污染）
 * @returns {string} 同源相对路径，或 fallback
 */
export function safeRedirectPath(raw, fallback = DEFAULT_FALLBACK) {
  return sanitize(raw) || sanitize(fallback) || DEFAULT_FALLBACK
}

export default safeRedirectPath
