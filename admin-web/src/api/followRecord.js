import request from '../utils/request'

export function listFollowRecords(params) {
  return request.get('/admin/followRecord/list', { params })
}

export function getFollowRecord(id) {
  return request.get(`/admin/followRecord/${id}`)
}

export function createFollowRecord(data) {
  const { adminId, ...body } = data || {}
  return request.post('/admin/followRecord', body, { params: { adminId } })
}

export function deleteFollowRecord(id) {
  return request.delete(`/admin/followRecord/${id}`)
}
