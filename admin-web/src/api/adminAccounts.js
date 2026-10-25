import request from '../utils/request'
const options = { skipErrorMessage: true }
export const listAccounts = params => request.get('/admin/accounts/list', { ...options, params })
export const listRoles = () => request.get('/admin/accounts/roles', options)
export const listAccountStores = () => request.get('/admin/accounts/coffee-stores', options)
export const createAccount = data => request.post('/admin/accounts', data, options)
export const updateAccount = (id, data) => request.put(`/admin/accounts/${encodeURIComponent(id)}`, data, options)
export const updateAccountAccess = (id, data) => request.put(`/admin/accounts/${encodeURIComponent(id)}/access`, data, options)
export const updateAccountStatus = (id, status) => request.put(`/admin/accounts/${encodeURIComponent(id)}/status`, { status }, options)
export const resetAccountPassword = (id, temporaryPassword) => request.post(`/admin/accounts/${encodeURIComponent(id)}/reset-password`, { temporaryPassword }, options)
