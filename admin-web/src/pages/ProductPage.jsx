import React, { useEffect, useMemo, useState } from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { createProduct, deleteProduct, getProduct, listProducts, updateProduct } from '../api/product'
import { listCategories } from '../api/category'
import { commonStatus, formatDateTime } from '../utils/format'

/**
 * 商品管理页面。
 */
export default function ProductPage() {
  const [categories, setCategories] = useState([])

  useEffect(() => {
    listCategories().then(data => {
      setCategories((Array.isArray(data) ? data : data?.records || []).map(item => ({
        label: `${item.name}（ID: ${item.id}）`,
        value: item.id
      })))
    })
  }, [])

  const categoryMap = useMemo(() => new Map(categories.map(item => [item.value, item.label])), [categories])

  return (
    <CrudTable
      title="商品"
      listApi={listProducts}
      detailApi={getProduct}
      createApi={createProduct}
      updateApi={updateProduct}
      deleteApi={deleteProduct}
      filters={[
        { name: 'keyword', label: '关键词' },
        { name: 'categoryId', label: '商品分类', type: 'select', options: categories },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '商品名称', dataIndex: 'name', width: 220 },
        { title: '商品分类', dataIndex: 'categoryId', width: 160, render: value => categoryMap.get(value) || value },
        { title: '销量', dataIndex: 'sales', width: 90 },
        { title: '状态', dataIndex: 'status', width: 90, render: commonStatus.text },
        { title: '排序', dataIndex: 'sort', width: 90 },
        { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'categoryId', label: '商品分类', type: 'select', options: categories, rules: [{ required: true, message: '请选择商品分类' }] },
        { name: 'name', label: '商品名称', rules: [{ required: true, message: '请输入商品名称' }] },
        { name: 'subtitle', label: '副标题' },
        { name: 'mainImage', label: '主图', type: 'image' },
        { name: 'images', label: '图片列表', type: 'textarea', placeholder: '多图 URL 用英文逗号分隔' },
        { name: 'videoUrl', label: '视频 URL' },
        { name: 'detail', label: '详情', type: 'textarea' },
        { name: 'effects', label: '功效说明', type: 'textarea' },
        { name: 'precautions', label: '注意事项', type: 'textarea' },
        { name: 'price', label: '默认价格', type: 'number', precision: 2 },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options },
        { name: 'sort', label: '排序', type: 'number' }
      ]}
    />
  )
}
