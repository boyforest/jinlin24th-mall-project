import request from '../utils/request'

export function listProducts(params) {
  return request.get('/admin/product/list', { params })
}

export function getProduct(id) {
  return request.get(`/admin/product/${id}`)
}

export function createProduct(data) {
  return request.post('/admin/product', data)
}

export function updateProduct(id, data) {
  return request.put(`/admin/product/${id}`, data)
}

export function deleteProduct(id) {
  return request.delete(`/admin/product/${id}`)
}
