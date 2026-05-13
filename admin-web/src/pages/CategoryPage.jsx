import React from 'react'
import CrudTable from '../components/CrudTable.jsx'
import { commonStatus, formatDateTime } from '../utils/format'
import { createCategory, deleteCategory, getCategory, listCategories, updateCategory } from '../api/category'

/**
 * 分类管理页面。
 */
export default function CategoryPage() {
  return (
    <CrudTable
      title="分类"
      listApi={listCategories}
      detailApi={getCategory}
      createApi={createCategory}
      updateApi={updateCategory}
      deleteApi={deleteCategory}
      filters={[
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
      columns={[
        { title: 'ID', dataIndex: 'id', width: 80 },
        { title: '父级ID', dataIndex: 'parentId', width: 100 },
        { title: '分类名称', dataIndex: 'name', width: 180 },
        { title: '排序', dataIndex: 'sort', width: 90 },
        { title: '状态', dataIndex: 'status', width: 90, render: commonStatus.text },
        { title: '更新时间', dataIndex: 'updateTime', width: 180, render: formatDateTime }
      ]}
      formFields={[
        { name: 'parentId', label: '父级ID', type: 'number' },
        { name: 'name', label: '分类名称', rules: [{ required: true, message: '请输入分类名称' }] },
        { name: 'icon', label: '图标 URL' },
        { name: 'image', label: '图片 URL' },
        { name: 'sort', label: '排序', type: 'number' },
        { name: 'status', label: '状态', type: 'select', options: commonStatus.options }
      ]}
    />
  )
}
