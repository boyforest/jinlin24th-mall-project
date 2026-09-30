/**
 * 退款失败提示文案。
 * <p>
 * 抽成纯函数便于用 node --test 覆盖：最容易出错的是“404 双重含义”——
 * 微信支付未启用时后端根本没注册该接口（404），而业务码 30504（支付记录不存在）
 * 同样是 HTTP 404，必须靠业务码优先区分，否则会把真实业务错误误报成“微信支付未启用”。
 */

/** 微信支付未启用（后端 wx.pay.enabled=false，控制器 @ConditionalOnProperty 未注册） */
export const WX_PAY_DISABLED_MESSAGE = '微信支付未启用，无法在线退款，请到微信商户平台处理'

/** 后端 BizCode 30504~30508 对应的可读文案 */
export const REFUND_ERROR_MESSAGES = {
  30504: '支付记录不存在，无法退款',
  30505: '当前订单状态不可退款（仅待发货、待收货可退）',
  // 当前仅支持全额退款。浏览器若缓存了旧版管理端包，仍可能提交部分金额而撞到此码，
  // 因此文案必须说明「不支持部分退款」，而不是含糊地说「金额不合法」让人反复试。
  30506: '仅支持全额退款，金额须等于实付金额',
  30507: '该订单已有退款正在处理中，请勿重复提交',
  30508: '该订单已全额退款，不能重复退'
}

/**
 * 根据错误对象给出退款失败提示文案。
 *
 * @param {{ code?: number, status?: number, message?: string }} error 响应拦截器抛出的错误
 * @returns {string} 面向管理员的中文提示
 */
export function refundErrorMessage(error) {
  const businessMessage = REFUND_ERROR_MESSAGES[error?.code]
  if (businessMessage) {
    return businessMessage
  }
  if (error?.status === 404) {
    return WX_PAY_DISABLED_MESSAGE
  }
  return error?.message || '退款失败'
}
