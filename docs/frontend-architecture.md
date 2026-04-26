# Frontend 技术架构（中枢管理界面）

> 版本：v0.6
> 日期：2026-04-25
> 作者：fanqibu

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v0.6 | 2026-04-26 | §3 项目结构补充 docs/ 目录；§4.1 Portal 路由补充 docs 分组说明；菜单结构调整：Portal 新增 API 文档菜单（docs/）、Admin 菜单改为「运营管理/AI平台/系统」三分组 |
| v0.5 | 2026-04-25 | §9.3 条件节点 SpEL 表达式增加安全警告（白名单校验 + SimpleEvaluationContext） |
| v0.4 | 2026-04-25 | **M-2**：§4 路由说明修正，「中枢内部路由」改为准确的「Backend 面向 Portal/Admin 的 API」；§9 编号修正（§9 重复 → §9/§10/§11）；检索测试补充标准返回格式 JSON；条件节点补充 SpEL 表达式 else 分支示例；配置下发标注为异步下发；新增 L-2 SpEL 表达式示例补充 |
| v0.3 | 2026-04-25 | 引入「中枢定位」章节，全文贯穿中枢（Frontend+Backend）与大脑（Edge）概念分层 |
| v0.2 | 2026-04-25 | 补充认证方案（JWT+Cookie）、状态管理、API 请求层设计 |
| v0.1 | 2026-04-25 | 初始版本 |

---

---

## 0. 中枢定位

Frontend 是**中枢（Central）的可视化管控界面**，在整个架构中的角色如下：

```
┌─────────────────────────────────────────────────────────────┐
│                        中枢（Central）                        │
│                                                             │
│   Frontend（管控台）  ←→  Backend（核心服务）  ←→  Edge（大脑） │
│                                                             │
│   · 用户操作入口，配置管理的可视化界面                        │
│   · 不直接执行 AI 推理，通过 Backend API 管理配置             │
│   · 展示大脑状态、调用统计、配置下发进度                      │
└─────────────────────────────────────────────────────────────┘
```

Frontend 的职责边界：
- ✅ RAG/Agent/Workflow 配置的增删改查界面
- ✅ 流程编排可视化画布（ReactFlow）
- ✅ 调用统计图表、配额使用看板
- ✅ 配置下发状态监控、大脑健康状态展示
- ❌ 不执行 AI 推理（由 Edge 大脑执行）
- ❌ 不存储配置（配置存储在 Backend 中枢服务）

---

## 1. 整体定位

Frontend 包含两个应用，统一在 **Next.js Monorepo** 中管理：

| 应用 | 路径前缀 | 目标用户 | 定位 |
|------|----------|---------|------|
| Developer Portal | `/portal` | 外部开发者/租户 | 中枢 — 租户级 AI 配置管理界面 |
| Admin Console | `/admin` | 平台运营人员 | 中枢 — 平台级 AI 监管与调度界面 |

---

## 2. 技术选型

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Next.js | 15.x | App Router，支持 SSR/SSG/CSR |
| 语言 | TypeScript | 5.x | 严格类型 |
| UI 组件库 | Ant Design | 5.x | 完整后台 UI 组件生态 |
| 状态管理 | Zustand | 4.x | 轻量级，Hooks 风格 |
| 数据请求 | TanStack Query | 5.x | 服务端状态管理、缓存、重验证 |
| HTTP 客户端 | Axios | 1.x | 统一拦截器、错误处理 |
| 表单 | React Hook Form + Zod | - | 表单验证 |
| 图表 | Ant Design Charts / ECharts | - | 调用统计可视化 |
| 代码编辑器 | Monaco Editor | - | Agent 提示词编辑 |
| 流程编排 | ReactFlow | 11.x | Workflow 可视化画布 |
| TTS/ASR | 阿里云 DashScope SDK | - | 调试面板语音测试 |
| 代码规范 | ESLint + Prettier | - | 统一代码风格 |
| 构建工具 | Turbopack（Next.js 内置） | - | 极速构建 |

---

## 3. 项目结构

```
frontend/
├── apps/
│   ├── portal/                  开发者控制台（中枢 — 租户级）
│   │   ├── app/                 Next.js App Router
│   │   │   ├── (auth)/          认证相关路由（登录/注册/找回密码）
│   │   │   ├── (dashboard)/     控制台路由（需登录）
│   │   │   │   ├── overview/    概览首页
│   │   │   │   ├── credentials/ AppKey 管理
│   │   │   │   ├── logs/        调用日志
│   │   │   │   ├── webhooks/    Webhook 管理
│   │   │   │   ├── docs/        API 文档（内嵌 Swagger UI）
│   │   │   │   ├── rag/         RAG 知识库管理
│   │   │   │   ├── agent/       Agent 配置管理
│   │   │   │   └── workflow/    Workflow 编排
│   │   │   ├── layout.tsx
│   │   │   └── page.tsx
│   │   ├── components/          Portal 专属组件
│   │   └── lib/               Portal 专属工具
│   │
│   └── admin/                   管理后台（中枢 — 平台级）
│       ├── app/
│       │   ├── (auth)/          Admin 登录
│       │   ├── (dashboard)/
│       │   │   ├── overview/    系统概览
│       │   │   ├── tenants/     租户管理
│       │   │   ├── plans/       套餐/配额管理
│       │   │   ├── logs/        全局调用日志
│       │   │   ├── rag/         全平台 RAG 管理
│       │   │   ├── agent/       全平台 Agent 管理
│       │   │   ├── workflow/    全平台 Workflow 管理
│       │   │   ├── brain-configs/  大脑配置下发状态
│       │   │   └── settings/    系统设置
│       │   ├── layout.tsx
│       │   └── page.tsx
│       ├── components/
│       └── lib/
│
├── packages/
│   ├── ui/                      共享 UI 组件库
│   │   ├── components/
│   │   └── styles/
│   ├── api-client/              共享 API 请求层
│   │   ├── portal/              Portal API 接口定义
│   │   └── admin/               Admin API 接口定义
│   ├── types/                   共享 TypeScript 类型
│   └── utils/                   共享工具函数
│
├── package.json                 Workspace 根配置
├── turbo.json                   Turborepo 构建配置
└── tsconfig.json                根 TypeScript 配置
```

---

## 4. 路由设计

### 4.1 Developer Portal 路由（Backend 面向租户的 API）

```
/portal                        → 重定向到 /portal/login
/portal/login                  → 登录
/portal/register               → 租户注册
/portal/forgot-password        → 找回密码

/portal/overview               → 控制台首页（调用量图表、近期日志）
/portal/credentials            → AppKey 列表
/portal/credentials/create     → 创建 AppKey
/portal/credentials/[id]       → AppKey 详情/编辑
/portal/logs                   → 调用日志（分页、筛选）
/portal/webhooks               → Webhook 配置列表
/portal/webhooks/create        → 创建 Webhook
/portal/docs                   → API 文档（内嵌 Swagger UI）
/portal/settings               → 租户设置（基本信息、配额查看）

# RAG 知识库管理
/portal/rag                    → RAG 知识库列表
/portal/rag/create             → 创建知识库
/portal/rag/[id]              → 知识库详情（文档管理、向量化配置）

# Agent 配置管理
/portal/agent                  → Agent 配置列表
/portal/agent/create           → 创建 Agent
/portal/agent/[id]             → Agent 详情（角色、提示词、TTS/ASR 配置）

# Workflow 编排
/portal/workflow                → Workflow 流程列表
/portal/workflow/create        → 创建 Workflow
/portal/workflow/[id]          → Workflow 编排画布（可视化节点编辑）
/portal/workflow/[id]/version  → Workflow 版本历史
```

### 4.2 Admin Console 路由（Backend 面向 Admin 的 API）

```
/admin                         → 重定向到 /admin/login
/admin/login                   → Admin 登录

/admin/overview                → 平台数据大盘
/admin/tenants                 → 租户列表
/admin/tenants/[id]            → 租户详情
/admin/plans                   → 套餐管理
/admin/plans/create            → 创建套餐
/admin/logs                    → 全平台调用日志
/admin/settings                → 系统设置

# 全平台 AI 配置管理
/admin/rag                     → 全平台 RAG 知识库管理
/admin/agent                   → 全平台 Agent 配置管理
/admin/workflow                → 全平台 Workflow 管理
/admin/brain-configs           → 租户大脑配置下发状态监控
```

---

## 5. 状态管理方案

### 5.1 服务端状态 — TanStack Query

用于所有 API 数据的获取、缓存、自动重新验证：

```typescript
// hooks/useAppKeys.ts
export function useAppKeys() {
  return useQuery({
    queryKey: ['app-keys'],
    queryFn: () => portalApi.getAppKeys(),
    staleTime: 30_000,
  });
}
```

### 5.2 客户端状态 — Zustand

用于用户信息、全局 UI 状态：

```typescript
// store/auth.ts
interface AuthState {
  user: UserInfo | null;
  tenantId: string | null;
  setUser: (user: UserInfo) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  tenantId: null,
  setUser: (user) => set({ user }),
  logout: () => set({ user: null, tenantId: null }),
}));
```

---

## 6. API 请求层设计

### 6.1 Axios 实例

```typescript
// packages/api-client/src/base.ts
const createClient = (baseURL: string) => axios.create({
  baseURL,
  timeout: 10_000,
  withCredentials: true,  // 自动携带 Cookie（HTTP-Only）
});

export const portalClient = createClient(process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080');
export const adminClient = createClient(process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080');

// 请求拦截：Cookie 自动携带，无需手动设置 Authorization
// 响应拦截：统一错误处理
client.interceptors.response.use(
  (res) => res.data,
  (error) => {
    if (error.response?.status === 401) {
      redirectToLogin();
    }
    return Promise.reject(error);
  }
);
```

### 6.2 API 接口定义示例

```typescript
// packages/api-client/src/portal/credentials.ts
import { portalClient } from '../base';

export const credentialsApi = {
  list: (): Promise<AppKey[]> =>
    portalClient.get('/api/portal/v1/credentials'),

  create: (data: CreateAppKeyRequest): Promise<AppKey> =>
    portalClient.post('/api/portal/v1/credentials', data),

  disable: (id: string): Promise<void> =>
    portalClient.put(`/api/portal/v1/credentials/${id}/disable`),

  delete: (id: string): Promise<void> =>
    portalClient.delete(`/api/portal/v1/credentials/${id}`),
};
```

---

## 7. 认证方案

### 7.1 JWT + HTTP-Only Cookie

- 登录成功后，后端设置 `Set-Cookie: access_token=xxx; HttpOnly; Secure; SameSite=Strict`
- Next.js Server Component 可直接读取 Cookie 传给 Backend（SSR 场景）
- 客户端 Axios 请求自动携带 Cookie（`withCredentials: true`）

> **注意**：采用 Cookie 方案时，Axios **不需要**手动设置 `Authorization` 头。`withCredentials: true` 会自动携带 Cookie。

### 7.2 路由守卫

```typescript
// middleware.ts（Next.js Middleware）
export function middleware(request: NextRequest) {
  const token = request.cookies.get('access_token');
  const pathname = request.nextUrl.pathname;

  // Portal 路由保护（排除登录/注册等公开页面）
  if (pathname.startsWith('/portal/') &&
      !pathname.startsWith('/portal/login') &&
      !pathname.startsWith('/portal/register') &&
      !pathname.startsWith('/portal/forgot-password')) {
    if (!token) {
      return NextResponse.redirect(new URL('/portal/login', request.url));
    }
  }

  // Admin 路由保护
  if (pathname.startsWith('/admin/') && !pathname.startsWith('/admin/login')) {
    if (!token) {
      return NextResponse.redirect(new URL('/admin/login', request.url));
    }
  }
}
```

---

## 8. 核心页面设计

### 8.1 Portal — 控制台首页

- **调用量折线图**：近 7 天/30 天，按天聚合
- **成功率指标**：成功/失败/4xx/5xx 占比饼图
- **AppKey 列表概览**：快速查看活跃 Key
- **最近调用日志**：最近 10 条，带状态徽标

### 8.2 Portal — AppKey 管理

| 功能 | 说明 |
|------|------|
| 创建 AppKey | 生成 AppKey + SecretKey，SecretKey 仅显示一次 |
| 查看 | 显示 AppKey（SecretKey 脱敏，支持一键复制） |
| 禁用/启用 | 软禁用，立即生效（Edge 缓存 5 分钟内失效） |
| 轮换 SecretKey | 生成新 Secret，旧 Secret 宽限期 24h 内同时有效 |
| 删除 | 软删除，历史日志保留 |

### 8.3 Portal — 调用日志

- 时间范围筛选
- 按状态码、接口路径、AppKey 筛选
- 导出 CSV

### 8.4 Admin — 租户管理

- 租户列表（搜索、分页）
- 租户详情：基本信息 + 绑定套餐 + 使用量统计
- 手动调整配额
- 禁用/恢复租户

---

## 9. RAG / Agent / Workflow 管理界面

### 9.1 RAG 知识库管理

**知识库列表页**：
- 表格展示：名称、文档数、向量化状态、创建时间
- 操作：编辑、删除、查看文档

**知识库详情页**：
- **基本信息**：名称、描述、向量化模型、维度
- **检索配置**：Top-K、相似度阈值
- **文档管理**：
  - 上传文档（支持 PDF/Word/TXT/Markdown），调用 `/api/portal/v1/rag/{id}/documents`
  - 文档列表：标题、状态、分块数
  - 重新向量化按钮，调用 `/api/portal/v1/rag/{id}/reindex`
- **检索测试**：输入查询文本，调用 `/api/portal/v1/rag/{id}/search`，返回格式：
  ```json
  {
    "code": 0,
    "data": [
      { "chunk": "文档片段内容...", "score": 0.85, "title": "文档标题" },
      { "chunk": "另一段内容...", "score": 0.72, "title": "文档标题" }
    ]
  }
  ```

### 9.2 Agent 配置管理

**Agent 列表页**：
- 表格展示：名称、关联模型、状态、创建时间
- 操作：编辑、删除、复制

**Agent 详情页**：
- **基础配置**：
  - 名称、描述
  - 系统提示词（Monaco Editor，支持变量插值）
  - LLM 模型选择（下拉框）
  - Temperature / Max Tokens 滑块
- **工具绑定**：
  - 工具列表（多选）：搜索、计算器、代码执行等
  - 工具参数配置
- **TTS 配置**（折叠面板）：
  - 启用开关
  - 音色选择（xiaoyun/xiaogang 等）
  - 语速 / 音调 / 音量滑块（0.5 ~ 2.0）
- **ASR 配置**（折叠面板）：
  - 启用开关
  - 语言选择（zh-CN/en-US 等）
  - 模型选择
  - 标点/中间结果开关
- **关联 RAG**：选择已创建的知识库（可选）
- **调试面板**：输入测试消息，实时展示 Agent 响应，调用 `/api/portal/v1/agent/{id}/debug`

### 9.3 Workflow 流程编排

**Workflow 列表页**：
- 表格展示：名称、版本、状态、创建时间
- 操作：编辑、发布、复制、删除

**Workflow 编排画布**（核心页面）：
- **左侧节点面板**：拖拽式节点库
  - 开始节点（Start）
  - LLM 节点（选择 Agent 配置）
  - RAG 节点（选择知识库）
  - 工具节点（选择工具）
  - 条件节点（IF/ELSE 分支，条件表达式支持受限 SpEL 语法，如 `{{input.age}} > 18`）
    > **安全警告**：SpEL 是图灵完备的表达式引擎，存在远程代码执行（RCE）风险。Backend 必须对租户输入的表达式做**白名单校验**（仅允许比较、逻辑运算、属性访问），禁止 `T()` 类型引用、`Runtime` 调用等危险操作。推荐使用 `SimpleEvaluationContext`（Spring 内置安全沙箱）替代默认的 `StandardEvaluationContext`。
  - TTS 节点（语音合成）
  - ASR 节点（语音识别）
  - 结束节点（End）
- **中央画布**：
  - 拖拽放置节点
  - 连线建立节点关系
  - 点击节点编辑配置（右侧抽屉）
  - 支持缩放、平移
- **右侧配置抽屉**：
  - 根据节点类型展示不同配置表单
  - LLM 节点：选择 Agent、覆盖提示词
  - RAG 节点：选择知识库、Top-K
  - TTS 节点：选择音色、语速
  - 条件节点：设置判断表达式
- **顶部工具栏**：
  - 保存草稿
  - 发布（创建新版本）
  - 运行测试（输入参数，执行流程），调用 `/api/portal/v1/workflow/{id}/run`
  - 版本历史（切换查看旧版本），调用 `/api/portal/v1/workflow/{id}/versions`

**版本管理**：
- 每次发布创建新版本（乐观锁）
- 支持版本对比（差异高亮）
- 支持回滚到历史版本，调用 `/api/portal/v1/workflow/{id}/rollback`

### 9.4 配置下发状态

**下发状态页**（Admin）：
- 表格展示：租户、配置版本、下发状态（pending/dispatched/failed）、下发时间、Edge 确认时间
- 操作：手动触发重下发（调用 `/api/admin/v1/brain-configs/{tenantId}/dispatch`，**异步下发，不等待 ACK**），查看配置快照
- 状态颜色：pending（黄色）/ dispatched（绿色）/ failed（红色）
- 下发失败排查：显示失败原因（版本冲突/Edge 不在线/超时），可复制 requestId 查日志

---

## 10. 构建与部署

### 10.1 环境准备

```bash
# 前置要求
node -v   # >= 22.0.0
npm install -g pnpm   # 安装 pnpm

# 初始化项目
cd frontend
pnpm install
```

### 10.2 开发命令

```bash
# 启动 Portal 开发服务
pnpm --filter portal dev

# 启动 Admin 开发服务
pnpm --filter admin dev

# 构建所有
pnpm build
```

### 10.3 环境变量

```bash
# .env.local
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080    # 指向 Edge Gateway
NEXT_PUBLIC_APP_ENV=development
```

### 10.4 生产部署

- 编译为静态文件或 Node.js 服务（`next start`）
- 通过 Nginx 反向代理，Portal 和 Admin 分别绑定不同子域名或路径
- 静态资源上传 CDN

---

## 11. 代码规范

### 11.1 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 组件 | PascalCase | `AppKeyCard.tsx` |
| Hooks | camelCase，use 前缀 | `useAppKeys.ts` |
| 工具函数 | camelCase | `formatTimestamp.ts` |
| 类型定义 | PascalCase | `AppKey`, `TenantInfo` |
| 常量 | UPPER_SNAKE_CASE | `MAX_APP_KEYS` |

### 11.2 目录原则

- 每个功能模块放在独立目录，包含组件 + Hooks + 类型
- 共享逻辑提取到 `packages/` 下
- Server Components 优先，Client Components 按需使用（标注 `'use client'`）