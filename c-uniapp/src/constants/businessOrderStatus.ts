export const businessOrderStatusText = {
  PENDING_PAY: '待付款', PENDING_SHIP: '待发货', PENDING_RECEIVE: '待收货', PAID: '待接单', MAKING: '制作中', READY: '待取餐', COMPLETED: '已完成', CANCELLED: '已取消', REFUNDING: '退款中', REFUNDED: '已退款',
}
export type BusinessOrderStatus = keyof typeof businessOrderStatusText
export const statusGroupText = { ALL: '全部', IN_PROGRESS: '进行中', COMPLETED: '已完成', CLOSED: '已关闭', AFTER_SALES: '售后' }
export type StatusGroup = keyof typeof statusGroupText
export const goodsStatuses: BusinessOrderStatus[] = ['PENDING_PAY', 'PENDING_SHIP', 'PENDING_RECEIVE', 'COMPLETED', 'CANCELLED', 'REFUNDING', 'REFUNDED']
export const coffeeStatuses: BusinessOrderStatus[] = ['PENDING_PAY', 'PAID', 'MAKING', 'READY', 'COMPLETED', 'CANCELLED', 'REFUNDING', 'REFUNDED']
