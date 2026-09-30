import { test } from 'node:test'
import assert from 'node:assert/strict'
import { inventoryWarehouseVisibility, isSingleWarehouseMode } from './inventoryView.js'

// 单仓模式 = 启用仓库恰好一个，由后端下发。前端不自己数（列表接口分页，数不准）。

test('后端明确返回 true 才算单仓模式', () => {
  assert.equal(isSingleWarehouseMode({ singleWarehouse: true }), true)
})

test('单仓模式下，仓库筛选/列/表单/详情全部隐藏', () => {
  const vis = inventoryWarehouseVisibility({ singleWarehouse: true, defaultWarehouseName: '主仓库' })
  assert.equal(vis.singleWarehouse, true)
  assert.equal(vis.showWarehouseFilter, false)
  assert.equal(vis.showWarehouseColumn, false)
  assert.equal(vis.showWarehouseFormField, false)
  assert.equal(vis.showWarehouseDetailRow, false)
})

test('多仓模式下，仓库相关元素全部保留（行为不变）', () => {
  const vis = inventoryWarehouseVisibility({ singleWarehouse: false })
  assert.equal(vis.singleWarehouse, false)
  assert.equal(vis.showWarehouseFilter, true)
  assert.equal(vis.showWarehouseColumn, true)
  assert.equal(vis.showWarehouseFormField, true)
  assert.equal(vis.showWarehouseDetailRow, true)
})

test('字段缺失/响应异常时，必须回退到多仓（显示显式仓库选择）', () => {
  // 这是最关键的一条：判定失败绝不能隐藏仓库选择，
  // 否则运营可能在不知情的情况下把库存写进非预期的仓。
  for (const bad of [
    undefined,
    null,
    {},
    { singleWarehouse: 'true' }, // 字符串 "true" 是常见接口/序列化错误形态
    { singleWarehouse: 1 },
    { singleWarehouse: 'yes' }
  ]) {
    const vis = inventoryWarehouseVisibility(bad)
    assert.equal(vis.singleWarehouse, false, `输入 ${JSON.stringify(bad)} 必须按多仓处理`)
    assert.equal(vis.showWarehouseFilter, true)
    assert.equal(vis.showWarehouseFormField, true, '仓库字段必须仍可填，不能替运营猜仓位')
  }
})
