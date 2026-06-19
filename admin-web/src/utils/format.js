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

export const couponType = {
  options: [
    { label: '满减券', value: 1 },
    { label: '折扣券', value: 2 },
    { label: '固定金额券', value: 3 }
  ],
  text: value => ({ 1: '满减券', 2: '折扣券', 3: '固定金额券' }[value] ?? value)
}

export const customerSource = {
  options: [
    { label: '小程序注册', value: 1 },
    { label: '销售录入', value: 2 },
    { label: '转介绍', value: 3 }
  ],
  text: value => ({ 1: '小程序注册', 2: '销售录入', 3: '转介绍' }[value] ?? value)
}

export const customerLevel = {
  options: [
    { label: '普通', value: 1 },
    { label: '重要', value: 2 },
    { label: 'VIP', value: 3 }
  ],
  text: value => ({ 1: '普通', 2: '重要', 3: 'VIP' }[value] ?? value)
}

export const followType = {
  options: [
    { label: '电话', value: 1 },
    { label: '微信', value: 2 },
    { label: '上门', value: 3 },
    { label: '其他', value: 4 }
  ],
  text: value => ({ 1: '电话', 2: '微信', 3: '上门', 4: '其他' }[value] ?? value)
}

export const inventoryLogType = {
  options: [
    { label: '入库', value: 1 },
    { label: '出库', value: 2 },
    { label: '盘点调整', value: 3 }
  ],
  text: value => ({ 1: '入库', 2: '出库', 3: '盘点调整' }[value] ?? value)
}

export const activityPosition = {
  options: [
    { label: '首页横幅', value: 'home_banner' },
    { label: '首页公告', value: 'home_notice' },
    { label: '登录弹窗', value: 'login_popup' }
  ],
  text: value => ({ home_banner: '首页横幅', home_notice: '首页公告', login_popup: '登录弹窗' }[value] ?? value)
}

export const activityLinkType = {
  options: [
    { label: '不跳转', value: 'none' },
    { label: '商品', value: 'product' },
    { label: '分类', value: 'category' },
    { label: '页面', value: 'page' }
  ],
  text: value => ({ none: '不跳转', product: '商品', category: '分类', page: '页面' }[value] ?? value)
}

export function formatMoney(value) {
  if (value === null || value === undefined || value === '') return ''
  return `¥${Number(value).toFixed(2)}`
}

export function formatDateTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 19)
}
