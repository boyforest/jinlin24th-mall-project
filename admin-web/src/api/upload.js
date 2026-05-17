import request from '../utils/request'

export function uploadImage(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/admin/upload/image', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
