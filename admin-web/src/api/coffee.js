import request from '../utils/request'
const options = { skipErrorMessage: true }
export const getCoffeeContext = () => request.get('/admin/coffee/context', options)
export const getCoffeeStore = id => request.get(`/admin/coffee/store/${encodeURIComponent(id)}`, options)
export const saveCoffeeStore = (id, data) => id ? request.put(`/admin/coffee/store/${encodeURIComponent(id)}`, data, options) : request.post('/admin/coffee/store', data, options)
export const getCoffeeMenu = storeId => request.get('/admin/coffee/menu', { ...options, params: { storeId } })
export const saveCoffeeCategory = (storeId, id, data) => id ? request.put(`/admin/coffee/category/${encodeURIComponent(id)}`, data, options) : request.post('/admin/coffee/category', data, { ...options, params: { storeId } })
export const saveCoffeeProduct = (storeId, id, data) => id ? request.put(`/admin/coffee/product/${encodeURIComponent(id)}`, data, options) : request.post('/admin/coffee/product', data, { ...options, params: { storeId } })
