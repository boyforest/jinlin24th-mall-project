import { apiRequest } from '@/api/client'

export interface ProductCategoryVO {
  id: number
  parentId?: number
  name: string
  icon?: string
  image?: string
  sort?: number
  status?: number
}

/**
 * 查询 C 端可见商品分类。
 */
export function listCategories() {
  return apiRequest<ProductCategoryVO[]>('/user/product/category/list', { auth: false })
}
