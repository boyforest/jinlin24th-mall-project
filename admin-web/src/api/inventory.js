import request from '../utils/request'

/**
 * 查询库存管理模式（是否单仓）。
 * 单仓模式下「库存」页隐藏"仓库"维度，运营只需面向 SKU 维护数量。
 */
export function getInventoryMode() {
  return request.get('/admin/inventory/mode')
}

export function listInventories(params) {
  return request.get('/admin/inventory/list', { params })
}

export function getInventory(id) {
  return request.get(`/admin/inventory/${id}`)
}

export function updateInventory(id, data) {
  return request.put(`/admin/inventory/${id}`, data)
}

export function listInventoryLogs(params) {
  return request.get('/admin/inventory/log/list', { params })
}

export function getInventoryLog(id) {
  return request.get(`/admin/inventory/log/${id}`)
}
