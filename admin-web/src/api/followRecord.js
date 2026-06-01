import request from '../utils/request'

export function listFollowRecords(params) {
  return request.get('/admin/followRecord/list', { params })
}

export function getFollowRecord(id) {
  return request.get(`/admin/followRecord/${id}`)
}

export function createFollowRecord(data) {
  // adminId 由后端从 JWT 提取，前端不再传参
  const { adminId, ...body } = data || {}
  return request.post('/admin/followRecord', body)
}

export function deleteFollowRecord(id) {
  return request.delete(`/admin/followRecord/${id}`)
}
