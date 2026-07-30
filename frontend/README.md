# Taxroom Vue 工作台

这是 `rcszh-tax` 的 Vue 3 + Vite 前端。Vite 只负责本地开发和构建，生产环境不需要单独运行 Node 服务：构建后的文件会直接写入 Spring Boot 的 `src/main/resources/static/workbench`，由 `/workbench` Controller 提供。

## 开发

```bash
cd frontend
npm install
npm run dev
```

开发地址为 `http://localhost:5173/workbench/`，Vite 会把 `/api/*` 代理到 `http://127.0.0.1:8080`。

## 生产构建

```bash
npm run build
```

构建完成后，启动 Spring Boot，访问 `http://localhost:8080/workbench/`。部署时只需要 Spring Boot 和数据库，不需要 Node 进程。

页面已对接：

- `POST /tasks`
- `GET /tasks/{taskId}`
- `PUT /tasks/items/{itemId}/review`
- `GET /exports/records/{taskId}`
- `GET /areas`
