import { listAdminOptions } from '../api/auth'
import { listCustomers } from '../api/customer'
import { listSkus } from '../api/sku'
import { listWarehouses } from '../api/warehouse'

export async function warehouseOptions(keyword) {
  const data = await listWarehouses({ page: 1, size: 50, status: 1, keyword })
  return toRecords(data).map(item => ({
    label: item.name || `仓库 ${item.id}`,
    value: item.id
  }))
}

export async function skuOptions(keyword) {
  const data = await listSkus({ page: 1, size: 50, status: 1, keyword })
  return toRecords(data).map(item => ({
    label: item.skuName || `SKU ${item.id}`,
    value: item.id
  }))
}

export async function customerOptions(keyword) {
  const data = await listCustomers({ page: 1, size: 50, status: 1, keyword })
  return toRecords(data).map(item => ({
    label: [item.name || `客户 ${item.id}`, item.contactPhone].filter(Boolean).join(' / '),
    value: item.id
  }))
}

export async function adminOptions(keyword) {
  const data = await listAdminOptions({ keyword })
  return (data || []).map(item => ({
    label: [item.realName || item.username || `管理员 ${item.id}`, item.phone].filter(Boolean).join(' / '),
    value: item.id,
    disabled: item.status === 0
  }))
}

function toRecords(data) {
  return Array.isArray(data) ? data : data?.records || []
}
