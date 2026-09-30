import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { refundErrorMessage, WX_PAY_DISABLED_MESSAGE } from './refundError.js'

/**
 * 退款失败文案单元测试（node --test）。
 */
describe('refundErrorMessage', () => {
  it('接口未注册（微信支付未启用）返回 404 时给出明确指引', () => {
    assert.equal(refundErrorMessage({ status: 404, message: 'Request failed with status code 404' }), WX_PAY_DISABLED_MESSAGE)
    assert.equal(refundErrorMessage({ status: 404 }), '微信支付未启用，无法在线退款，请到微信商户平台处理')
  })

  it('业务码优先于 HTTP 状态码：30504 也是 404，不能误报成未启用', () => {
    assert.equal(refundErrorMessage({ code: 30504, status: 404, message: '支付记录不存在' }), '支付记录不存在，无法退款')
  })

  it('覆盖 30504~30508 全部业务码', () => {
    assert.equal(refundErrorMessage({ code: 30505, status: 400 }), '当前订单状态不可退款（仅待发货、待收货可退）')
    assert.equal(refundErrorMessage({ code: 30506, status: 400 }), '仅支持全额退款，金额须等于实付金额')
    assert.equal(refundErrorMessage({ code: 30507, status: 409 }), '该订单已有退款正在处理中，请勿重复提交')
    assert.equal(refundErrorMessage({ code: 30508, status: 400 }), '该订单已全额退款，不能重复退')
  })

  it('旧版管理端包提交部分金额时，提示必须说明不支持部分退款', () => {
    // 浏览器缓存了旧 bundle 仍可能带 refundAmount 提交，后端以 30506 拒绝。
    // 文案要让管理员知道「这是不支持部分退款」，而不是让他反复试金额。
    const message = refundErrorMessage({ code: 30506, status: 400 })
    assert.match(message, /全额退款/)
    assert.doesNotMatch(message, /金额不合法/)
  })

  it('未知错误回退到后端 message', () => {
    assert.equal(refundErrorMessage({ code: 99999, status: 500, message: '申请退款失败，请稍后重试' }), '申请退款失败，请稍后重试')
    assert.equal(refundErrorMessage({ message: 'Network Error' }), 'Network Error')
  })

  it('兜底：空错误对象也有可读文案', () => {
    assert.equal(refundErrorMessage({}), '退款失败')
    assert.equal(refundErrorMessage(undefined), '退款失败')
  })
})
