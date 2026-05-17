import request from '../utils/request'

export function listMarketingActivities(params) {
  return request.get('/admin/marketing/activity/list', { params })
}

export function getMarketingActivity(id) {
  return request.get(`/admin/marketing/activity/${id}`)
}

export function createMarketingActivity(data) {
  return request.post('/admin/marketing/activity', data)
}

export function updateMarketingActivity(id, data) {
  return request.put(`/admin/marketing/activity/${id}`, data)
}

export function deleteMarketingActivity(id) {
  return request.delete(`/admin/marketing/activity/${id}`)
}
