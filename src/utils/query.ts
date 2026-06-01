/**
 * 从对象构建 URL 查询字符串。
 * 过滤掉 undefined / null / 空字符串值。
 */
export function buildQuery(params: Record<string, unknown> = {}) {
  const entries = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)

  return entries.length ? `?${entries.join('&')}` : ''
}
