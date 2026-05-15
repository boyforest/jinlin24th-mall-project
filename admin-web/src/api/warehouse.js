import request from '../utils/request'

export function listWarehouses(params) {
  return request.get('/admin/warehouse/list', { params })
}

export function getWarehouse(id) {
  return request.get(`/admin/warehouse/${id}`)
}

export function createWarehouse(data) {
  return request.post('/admin/warehouse', data)
}

export function updateWarehouse(id, data) {
  return request.put(`/admin/warehouse/${id}`, data)
}

export function deleteWarehouse(id) {
  return request.delete(`/admin/warehouse/${id}`)
}
