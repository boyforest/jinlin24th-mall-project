import request from '../utils/request'

export function listCoupons(params) {
  return request.get('/admin/coupon/list', { params })
}

export function getCoupon(id) {
  return request.get(`/admin/coupon/${id}`)
}

export function createCoupon(data) {
  return request.post('/admin/coupon', data)
}

export function updateCoupon(id, data) {
  return request.put(`/admin/coupon/${id}`, data)
}

export function deleteCoupon(id) {
  return request.delete(`/admin/coupon/${id}`)
}
