/**
 * 订单状态映射。C 端展示用。
 */
export const ORDER_STATUS_MAP: Record<number, string> = {
  0: '待支付',
  10: '待发货',
  20: '待收货',
  30: '已完成',
  40: '已取消',
  50: '退款中',
  60: '已退款',
}

export function statusText(value?: number): string {
  if (value === undefined || value === null) return '-'
  return ORDER_STATUS_MAP[value] || `状态${value}`
}
