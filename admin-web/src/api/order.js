import request from '../utils/request'

export function listOrders(params) {
  return request.get('/admin/order/list', { params })
}

export function getOrder(id) {
  return request.get(`/admin/order/${id}`)
}

export function cancelOrder(id) {
  return request.post(`/admin/order/${id}/cancel`)
}

export function shipOrder(id) {
  return request.post(`/admin/order/${id}/ship`)
}

export function completeOrder(id) {
  return request.post(`/admin/order/${id}/complete`)
}
