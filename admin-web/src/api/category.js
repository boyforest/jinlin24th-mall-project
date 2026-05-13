import request from '../utils/request'

export function listCategories(params) {
  return request.get('/admin/product/category/list', { params })
}

export function getCategory(id) {
  return request.get(`/admin/product/category/${id}`)
}

export function createCategory(data) {
  return request.post('/admin/product/category', data)
}

export function updateCategory(id, data) {
  return request.put(`/admin/product/category/${id}`, data)
}

export function deleteCategory(id) {
  return request.delete(`/admin/product/category/${id}`)
}
