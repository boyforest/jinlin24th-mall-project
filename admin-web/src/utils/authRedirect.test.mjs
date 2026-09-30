import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { shouldRedirectToLogin } from './authRedirect.js'

/**
 * 401 跳转判定单元测试（node --test）。
 */
describe('shouldRedirectToLogin', () => {
  it('登录接口返回 401（密码错误）不跳转，否则会把错误提示刷掉', () => {
    assert.equal(shouldRedirectToLogin({ status: 401, url: '/admin/login', pathname: '/login' }), false)
    assert.equal(shouldRedirectToLogin({ status: 401, url: '/admin/login', pathname: '/orders' }), false)
  })

  it('改密接口返回 401（旧密码错误）同样不跳转', () => {
    assert.equal(shouldRedirectToLogin({ status: 401, url: '/admin/password', pathname: '/login' }), false)
    assert.equal(shouldRedirectToLogin({ status: 401, url: '/admin/password', pathname: '/orders' }), false)
  })

  it('已经停留在 /login 时不重复跳转', () => {
    assert.equal(shouldRedirectToLogin({ status: 401, url: '/admin/order/list', pathname: '/login' }), false)
  })

  it('其他已认证请求 401 时仍然照常跳转登录页', () => {
    assert.equal(shouldRedirectToLogin({ status: 401, url: '/admin/order/list', pathname: '/orders' }), true)
    assert.equal(shouldRedirectToLogin({ status: 401, url: '/admin/payment/refund', pathname: '/orders' }), true)
  })

  it('非 401 一律不跳转（404/409/500 等交给调用方处理）', () => {
    assert.equal(shouldRedirectToLogin({ status: 404, url: '/admin/payment/refund', pathname: '/orders' }), false)
    assert.equal(shouldRedirectToLogin({ status: 409, url: '/admin/payment/refund', pathname: '/orders' }), false)
    assert.equal(shouldRedirectToLogin({ status: 500, url: '/admin/order/list', pathname: '/orders' }), false)
    assert.equal(shouldRedirectToLogin({ url: '/admin/order/list', pathname: '/orders' }), false)
  })

  it('容错：url/pathname 缺失或参数为空时不抛错', () => {
    assert.equal(shouldRedirectToLogin({ status: 401 }), true)
    assert.equal(shouldRedirectToLogin(), false)
    assert.equal(shouldRedirectToLogin({ status: 401, url: null, pathname: null }), true)
  })
})
