# 金霖二十四养管理后台

## 初始化依赖

```bash
npm install react-router-dom antd axios @ant-design/icons
```

## 启动

```bash
npm run dev
```

默认访问：http://localhost:5173

## Vite 配置

`vite.config.js` 已配置：

- 开发端口：`5173`
- 代理：`/admin` -> `http://localhost:7878`
- 可通过 `VITE_API_TARGET` 修改后端地址

示例：

```bash
VITE_API_TARGET=http://localhost:7878 npm run dev
```

## 目录说明

- `src/main.jsx`：React 入口，挂载 Router 和 Ant Design 全局配置。
- `src/utils/request.js`：Axios 请求封装，自动携带 token，统一处理 Result 响应。
- `src/router/index.jsx`：React Router v6 路由配置，包含登录守卫和懒加载。
- `src/router/routes.jsx`：后台业务路由元数据，菜单和面包屑都从这里生成。
- `src/layouts/AdminLayout.jsx`：后台布局，包含可折叠侧边栏、顶部栏、面包屑和主内容区。
- `src/api`：接口模块，按业务拆分，保持后端接口参数和返回值不变。
- `src/pages`：页面组件。
- `src/components`：公共组件。
- `src/assets/global.css`：全局样式。

## 添加新页面

1. 在 `src/api` 新增对应接口模块。
2. 在 `src/pages` 新增页面组件。
3. 在 `src/router/routes.jsx` 增加一条路由元数据。

菜单和面包屑会自动出现。

## 分销管理

分销页面路径：`/distribution`

功能：

- 查询佣金记录
- 按状态筛选
- 可结算记录执行结算
- 导出 CSV
- 编辑分销配置

对应后端接口：

- `GET /admin/distribution/list`
- `GET /admin/distribution/{id}`
- `PUT /admin/distribution/settle`
- `GET /admin/distribution/export`
- `GET /admin/distribution/config`
- `PUT /admin/distribution/config`
