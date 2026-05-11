# 金霖二十四养管理后台前端

## 启动

```bash
cd admin-web
npm install
npm run dev
```

默认访问：

```text
http://localhost:5173
```

默认代理到后端：

```text
http://localhost:7878
```

如果后端端口不同：

```bash
VITE_API_TARGET=http://localhost:7879 npm run dev
```

## 登录

使用后端 `application.yml` 中的：

- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`

登录成功后 Token 会保存到浏览器 `localStorage`，后续请求自动携带：

```text
Authorization: Bearer <token>
```
