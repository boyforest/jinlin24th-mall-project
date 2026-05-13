export const commonStatus = {
  options: [
    { label: '启用', value: 1 },
    { label: '禁用', value: 0 }
  ],
  text: value => ({ 0: '禁用', 1: '启用' }[value] ?? value)
}

export const orderStatus = {
  options: [
    { label: '待付款', value: 0 },
    { label: '待发货', value: 10 },
    { label: '待收货', value: 20 },
    { label: '已完成', value: 30 },
    { label: '已取消', value: 40 },
    { label: '退款中', value: 50 },
    { label: '已退款', value: 60 }
  ],
  text: value => ({
    0: '待付款',
    10: '待发货',
    20: '待收货',
    30: '已完成',
    40: '已取消',
    50: '退款中',
    60: '已退款'
  }[value] ?? value)
}

export const distributorStatus = {
  options: [
    { label: '是', value: 1 },
    { label: '否', value: 0 }
  ],
  text: value => ({ 0: '否', 1: '是' }[value] ?? value)
}

export const distributionStatus = {
  options: [
    { label: '待结算', value: 0 },
    { label: '可结算', value: 1 },
    { label: '已结算', value: 2 },
    { label: '已退回', value: 3 }
  ],
  text: value => ({ 0: '待结算', 1: '可结算', 2: '已结算', 3: '已退回' }[value] ?? value)
}

export function formatMoney(value) {
  if (value === null || value === undefined || value === '') return ''
  return `¥${Number(value).toFixed(2)}`
}

export function formatDateTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}
