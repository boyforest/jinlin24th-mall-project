import React from 'react'
import { Tag } from 'antd'
import { commonStatus, distributionStatus, distributorStatus, formatDateTime, formatMoney, orderStatus } from './format'

export const adminMenuGroups = [
  {
    key: 'overview',
    label: '概览',
    children: ['/dashboard']
  },
  {
    key: 'goods',
    label: '商品中心',
    children: ['/products', '/skus', '/categories']
  },
  {
    key: 'trade',
    label: '交易中心',
    children: ['/orders', '/coupons', '/marketing-activities']
  },
  {
    key: 'warehouse',
    label: '仓储物流',
    children: ['/warehouses', '/inventory', '/inventory-logs']
  },
  {
    key: 'growth',
    label: '客户与分销',
    children: ['/customers', '/follow-records', '/users', '/distribution']
  }
]

export function statusTag(text, color) {
  if (!text) return '-'
  return <Tag color={color}>{text}</Tag>
}

export function renderIdentity(name, phone, id) {
  const displayName = name || (id ? `ID ${id}` : '-')
  if (!phone) return displayName
  return `${displayName} / ${phone}`
}

export function buildDescriptions(detail, fields = []) {
  return fields.map(field => {
    const rawValue = detail
      ? (typeof field.dataIndex === 'function' ? field.dataIndex(detail) : detail?.[field.dataIndex])
      : undefined
    const renderedValue = detail && field.renderDetail
      ? field.renderDetail(rawValue, detail)
      : renderValueByType(rawValue, field)
    return {
      key: field.key || field.label,
      label: field.label,
      children: isEmptyValue(renderedValue) ? '-' : renderedValue
    }
  })
}

function renderValueByType(value, field) {
  if (isEmptyValue(value)) {
    return '-'
  }
  switch (field.type) {
    case 'money':
      return formatMoney(value)
    case 'datetime':
      return formatDateTime(value)
    case 'status':
      return statusTag(commonStatus.text(value), value === 1 ? 'green' : 'default')
    case 'orderStatus':
      return statusTag(orderStatus.text(value), orderStatusColor(value))
    case 'distributionStatus':
      return statusTag(distributionStatus.text(value), distributionStatusColor(value))
    case 'distributorStatus':
      return statusTag(distributorStatus.text(value), value === 1 ? 'green' : 'default')
    default:
      return String(value)
  }
}

function isEmptyValue(value) {
  return value === null || value === undefined || value === ''
}

export function orderStatusColor(value) {
  return ({
    0: 'gold',
    10: 'blue',
    20: 'cyan',
    30: 'green',
    40: 'default',
    50: 'orange',
    60: 'red'
  })[value] || 'default'
}

export function distributionStatusColor(value) {
  return ({ 0: 'gold', 1: 'blue', 2: 'green', 3: 'red' })[value] || 'default'
}
