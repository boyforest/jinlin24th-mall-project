import request from '../utils/request'

export function listOrders(params) {
  return request.get('/admin/order/list', { params })
}

export function getOrder(id) {
  return request.get(`/admin/order/${id}`)
}

export function cancelOrder(id, adminId) {
  return request.post(`/admin/order/${id}/cancel`, null, { params: { adminId } })
}

export function shipOrder(id, adminId) {
  return request.post(`/admin/order/${id}/ship`, null, { params: { adminId } })
}

export function completeOrder(id, adminId) {
  return request.post(`/admin/order/${id}/complete`, null, { params: { adminId } })
}
