import request from '../utils/request'

export function listSkus(params) {
  return request.get('/admin/product/sku/list', { params })
}

export function getSku(id) {
  return request.get(`/admin/product/sku/${id}`)
}

export function createSku(data) {
  return request.post('/admin/product/sku', data)
}

export function updateSku(id, data) {
  return request.put(`/admin/product/sku/${id}`, data)
}

export function deleteSku(id) {
  return request.delete(`/admin/product/sku/${id}`)
}
