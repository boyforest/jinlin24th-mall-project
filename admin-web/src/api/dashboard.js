import { listProducts } from './product'
import { listOrders } from './order'
import { listUsers } from './user'
import { listDistributions } from './distribution'
import { listCoupons } from './coupon'
import { listInventories } from './inventory'
import { listCustomers } from './customer'

/**
 * 首页统计数据：复用现有分页接口，避免新增后端接口。
 */
export async function getDashboardStats() {
  const [products, orders, users, distributions, coupons, inventories, customers] = await Promise.all([
    listProducts({ page: 1, size: 1 }),
    listOrders({ page: 1, size: 1 }),
    listUsers({ page: 1, size: 1 }),
    listDistributions({ page: 1, size: 1 }),
    listCoupons({ page: 1, size: 1 }),
    listInventories({ page: 1, size: 1 }),
    listCustomers({ page: 1, size: 1 })
  ])
  return {
    products: products?.total || 0,
    orders: orders?.total || 0,
    users: users?.total || 0,
    distributions: distributions?.total || 0,
    coupons: coupons?.total || 0,
    inventories: inventories?.total || 0,
    customers: customers?.total || 0
  }
}
