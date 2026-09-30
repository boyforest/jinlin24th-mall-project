import request from '../utils/request'

/**
 * 订单退款（**当前仅支持全额**）。
 * <p>
 * 后端约定：退款金额由服务端从 payment_record 取实付金额，前端**不再传金额**。
 * 部分退款会使剩余金额无法再退（退款成功即置订单已退款并作废该订单全部佣金），
 * 因此后端也会拒绝任何不等于实付金额的入参。返回值为微信退款单号。
 * <p>
 * 注意：该接口仅在微信支付启用（后端 wx.pay.enabled=true）时才注册，
 * 未启用时返回 404，调用方需要按“微信支付未启用”提示，而不是报网络错误。
 * 这里 skipErrorMessage 由调用方统一按业务码给出更准确的中文提示。
 */
export function refundPayment(orderNo) {
  return request.post('/admin/payment/refund', null, {
    params: { orderNo },
    skipErrorMessage: true
  })
}
