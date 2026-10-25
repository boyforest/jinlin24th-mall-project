import test from 'node:test'
import assert from 'node:assert/strict'
import { canVisit, landingRoute } from './adminAccess.js'
import { businessDate, money } from './coffeeOrders.js'
test('咖啡账号拒绝商品和账号深链接，按服务端默认落页', () => {
  const context = { roles: ['COFFEE_ADMIN'], permissions: ['coffee:orders:read'], defaultRoute: '/coffee/workbench' }
  assert.equal(canVisit(context, '/system/accounts'), false)
  assert.equal(landingRoute(context, '/products'), '/coffee/workbench')
  assert.equal(landingRoute(context, '//evil.test'), '/coffee/workbench')
})
test('未知旧角色不升级权限，非法默认落页不放行', () => {
  assert.equal(landingRoute({ roles: ['UNKNOWN'], permissions: [], defaultRoute: '/dashboard' }), '/unauthorized')
  assert.equal(canVisit({ permissions: ['product:manage'] }, '/marketing-activities'), false)
})
test('安全且已授权的返回页保留查询参数', () => {
  assert.equal(landingRoute({ permissions: ['order:manage'], defaultRoute: '/orders' }, '/orders?keyword=123'), '/orders?keyword=123')
})
test('流水负数保留，业务日期使用上海而非本机时区', () => {
  assert.equal(money(-1200), '¥-12.00')
  assert.equal(businessDate('2026-10-25T17:00:00Z'), '2026-10-26')
})
