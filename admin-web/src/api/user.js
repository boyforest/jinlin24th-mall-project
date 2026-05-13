import request from '../utils/request'

export function listUsers(params) {
  return request.get('/admin/user/list', { params })
}

export function getUser(id) {
  return request.get(`/admin/user/${id}`)
}

export function updateUserStatus(params) {
  return request.put('/admin/user/status', null, { params })
}

export function updateDistributor(params) {
  return request.put('/admin/user/distributor', null, { params })
}
