import request from '../utils/request'

export function listDistributions(params) {
  return request.get('/admin/distribution/list', { params })
}

export function getDistribution(id) {
  return request.get(`/admin/distribution/${id}`)
}

export function settleDistribution(params) {
  return request.put('/admin/distribution/settle', null, { params })
}

export function exportDistribution(params) {
  return request.get('/admin/distribution/export', {
    params,
    responseType: 'blob'
  })
}

export function getDistributionConfig() {
  return request.get('/admin/distribution/config')
}

export function updateDistributionConfig(data) {
  return request.put('/admin/distribution/config', data)
}
