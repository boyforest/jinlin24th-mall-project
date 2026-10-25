import request from '../utils/request'
const options = { skipErrorMessage: true }
export const getWorkbench = storeId => request.get('/admin/coffee/workbench', { ...options, params: { storeId } })
export const listCoffeeOrders = params => request.get('/admin/coffee/orders/list', { ...options, params })
export const getCoffeeOrder = id => request.get(`/admin/coffee/orders/${encodeURIComponent(id)}`, options)
export const operateCoffeeOrder = (id, action, data) => request.post(`/admin/coffee/orders/${encodeURIComponent(id)}/${action.toLowerCase()}`, data, options)
export const getCoffeeLedger = params => request.get('/admin/coffee/ledger', { ...options, params })
