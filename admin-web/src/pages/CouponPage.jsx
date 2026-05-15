import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { createCoupon, deleteCoupon, getCoupon, listCoupons, updateCoupon } from '../api/coupon'
import { commonStatus, couponType, formatDateTime, formatMoney } from '../utils/format'

/**
 * 优惠券管理页面。
 */
export default function CouponPage() {
  return (
    <CrudTable
      title="优惠券"
      listApi={listCoupons}
      detailApi={getCoupon}
      createApi={createCoupon}
      updateApi={updateCoupon}
      deleteApi={deleteCoupon}
      filters={[
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '名称', dataIndex: 'name', width: 180 },
        { title: '类型', dataIndex: 'type', width: 110, render: couponType.text },
        { title: '门槛', dataIndex: 'minAmount', width: 110, render: formatMoney },
        { title: '优惠值', dataIndex: 'discountValue', width: 110 },
        { title: '库存', dataIndex: 'stock', width: 90 },
        { title: '领取', dataIndex: 'receivedCount', width: 90 },
        { title: '使用', dataIndex: 'usedCount', width: 90 },
        { title: '状态', dataIndex: 'status', width: 90, render: commonStatus.text },
        { title: '有效期开始', dataIndex: 'startTime', width: 180, render: formatDateTime },
        { title: '有效期结束', dataIndex: 'endTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'name', label: '优惠券名称', rules: [{ required: true, message: '请输入优惠券名称' }] },
        { name: 'type', label: '类型', type: 'select', options: couponType.options, rules: [{ required: true, message: '请选择类型' }] },
        { name: 'minAmount', label: '使用门槛', type: 'number', precision: 2 },
        { name: 'discountValue', label: '优惠值', type: 'number', precision: 2 },
        { name: 'stock', label: '发放总量', type: 'number' },
        { name: 'startTime', label: '生效时间', placeholder: '2026-05-15T00:00:00' },
        { name: 'endTime', label: '过期时间', placeholder: '2026-06-15T23:59:59' },
        { name: 'memberLevelId', label: '限定会员等级ID', type: 'number' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
    />
  )
}
