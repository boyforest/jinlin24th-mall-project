import request from '../utils/request'

export function listCustomers(params) {
  return request.get('/admin/customer/list', { params })
}

export function getCustomer(id) {
  return request.get(`/admin/customer/${id}`)
}

export function createCustomer(data) {
  return request.post('/admin/customer', data)
}

export function updateCustomer(id, data) {
  return request.put(`/admin/customer/${id}`, data)
}

export function deleteCustomer(id) {
  return request.delete(`/admin/customer/${id}`)
}
