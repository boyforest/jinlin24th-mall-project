/**
 * 格式化金额，避免页面里到处写 Number 兜底。
 */
export function money(value?: number | string | null): string {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}
