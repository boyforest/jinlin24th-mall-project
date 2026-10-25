import { test } from 'node:test'
import assert from 'node:assert/strict'
import { coffeeProductPayload, validCoffeeImage } from './coffeeMenu.js'
const base = { categoryId: '1', name: '咖啡', status: 1, sort: 0, skus: [{ name: '热 · 无糖', temperature: 'HOT', sugar: 'NONE', priceCent: 1800, status: 1, available: true }] }
test('恢复旧温度甜度组合时复用规格 ID，其他字段使用严格 JSON 类型', () => {
  const data = coffeeProductPayload(base, [{ id: '9007199254740994', temperature: 'HOT', sugar: 'NONE', status: 0 }])
  assert.equal(data.skus[0].id, '9007199254740994')
  assert.equal(data.skus[0].priceCent, 1800)
  assert.equal(data.skus[0].available, true)
  assert.equal(data.status, 1)
})
test('同一饮品不能提交重复组合、非整数金额或字符串状态', () => {
  assert.throws(() => coffeeProductPayload({ ...base, skus: [...base.skus, ...base.skus] }), /组合不能重复/)
  for (const value of [0, -1, 0.1, '1800', Number.MAX_SAFE_INTEGER + 1]) assert.throws(() => coffeeProductPayload({ ...base, skus: [{ ...base.skus[0], priceCent: value }] }), /整数分/)
  assert.throws(() => coffeeProductPayload({ ...base, skus: [{ ...base.skus[0], status: '1' }] }), /状态/)
  assert.throws(() => coffeeProductPayload({ ...base, skus: [{ ...base.skus[0], available: 1 }] }), /可售/)
})
test('移除规格省略旧行，保留的规格稳定 ID 不变', () => {
  const data = coffeeProductPayload(base, [{ id: '11', temperature: 'HOT', sugar: 'NONE' }, { id: '12', temperature: 'ICED', sugar: 'NONE' }])
  assert.deepEqual(data.skus.map(row => row.id), ['11'])
  assert.throws(() => coffeeProductPayload({ ...base, skus: [] }), /至少保留/)
})
test('上传路径使用 HTTPS 或认可的 uploads 路径，拒绝本地磁盘路径', () => {
  for (const url of [null, '', 'https://example.com/coffee.png', '/uploads/a.png']) assert.equal(validCoffeeImage(url), true)
  for (const url of ['/Users/me/image.png', 'file:///image.png', 'http://example.com/a.png', 'javascript:alert(1)']) assert.equal(validCoffeeImage(url), false)
})
