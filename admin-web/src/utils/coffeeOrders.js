export const stages = ['WAIT_ACCEPT', 'WAIT_MAKE', 'MAKING', 'READY']
export const fulfillment = { WAIT_PAYMENT: '待付款', WAIT_ACCEPT: '待接单', WAIT_MAKE: '待制作', MAKING: '制作中', READY: '待取餐', COMPLETED: '已取餐', CANCELLED: '已取消', REJECTED: '已拒单' }
export const payments = { UNPAID: '未支付', PAID: '已支付', CLOSED: '支付已关闭' }
export const refunds = { NONE: '未退款', PENDING: '退款处理中', SUCCESS: '退款成功', FAILED: '退款失败' }
export const notifications = { NONE: '未创建提醒', PENDING: '提醒待发送', SENT: '微信已受理（不代表已看到）', SKIPPED: '未发送（授权或模板条件不足）', FAILED: '提醒发送失败', UNKNOWN: '提醒结果不确定' }
export const actionLabels = { ACCEPT: '确认接单', START: '开始制作', READY: '制作完成', COMPLETE: '确认取走', REJECT: '无法接单', REFUND: '整单全额退款' }
export const actionPermissions = { ACCEPT: 'coffee:orders:operate', START: 'coffee:orders:operate', READY: 'coffee:orders:operate', COMPLETE: 'coffee:orders:operate', REJECT: 'coffee:orders:refund', REFUND: 'coffee:orders:refund' }
export const money = cent => cent == null ? '—' : `¥${(cent / 100).toFixed(2)}`
export const businessDate = (value = new Date()) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(value))
export const dateTime = value => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—'
export function waitTime(value, now = Date.now()) {
  if (!value) return '等待时间未知'
  const minutes = Math.max(0, Math.floor((now - new Date(value).getTime()) / 60000))
  return minutes >= 60 ? `${Math.floor(minutes / 60)}小时${minutes % 60}分钟` : `${minutes}分钟`
}
export function itemSpec(item) { return [item.specName, { HOT: '热', ICED: '冰' }[item.temperature] || item.temperature, { REGULAR: '标准糖', LESS: '少糖', NONE: '无糖' }[item.sugar] || item.sugar].filter(Boolean).join(' · ') }
