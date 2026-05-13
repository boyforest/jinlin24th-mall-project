import { listProducts } from './product'
import { listOrders } from './order'
import { listUsers } from './user'
import { listDistributions } from './distribution'

/**
 * 首页统计数据：复用现有分页接口，避免新增后端接口。
 */
export async function getDashboardStats() {
  const [products, orders, users, distributions] = await Promise.all([
    listProducts({ page: 1, size: 1 }),
    listOrders({ page: 1, size: 1 }),
    listUsers({ page: 1, size: 1 }),
    listDistributions({ page: 1, size: 1 })
  ])
  return {
    products: products?.total || 0,
    orders: orders?.total || 0,
    users: users?.total || 0,
    distributions: distributions?.total || 0
  }
}
