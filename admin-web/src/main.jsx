import React from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { App, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import 'antd/dist/reset.css'
import './assets/global.css'
import router from './router/index.jsx'

/**
 * 管理后台入口。
 * <p>
 * ConfigProvider 统一 Ant Design 中文语言和主题；App 组件提供 message、modal 等组合式 API 上下文。
 */
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#1f7a5b',
          borderRadius: 6
        }
      }}
    >
      <App>
        <RouterProvider router={router} />
      </App>
    </ConfigProvider>
  </React.StrictMode>
)
