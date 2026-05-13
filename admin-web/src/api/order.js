import request from '../utils/request'

export function listOrders(params) {
  return request.get('/admin/order/list', { params })
}

export function getOrder(id) {
  return request.get(`/admin/order/${id}`)
}
