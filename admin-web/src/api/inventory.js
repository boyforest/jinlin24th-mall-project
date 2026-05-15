import request from '../utils/request'

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
