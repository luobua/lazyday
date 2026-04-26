# Portal Login 页面设计

> 覆盖 MASTER.md 对应部分，未提及的规则继续遵循 MASTER。

---

## 页面定位

开发者登录入口。简洁、专业、快速完成登录操作。

## 布局

```
┌──────────────────────────────────────────────────────────┐
│                    全屏深色背景                            │
│                    #0F172A                                │
│                                                          │
│              ┌──────────────────────┐                    │
│              │    Logo + 产品名      │                    │
│              │    "Lazyday 开放平台"  │                    │
│              ├──────────────────────┤                    │
│              │                      │                    │
│              │  ┌────────────────┐  │                    │
│              │  │ 用户名          │  │                    │
│              │  └────────────────┘  │                    │
│              │  ┌────────────────┐  │                    │
│              │  │ 密码 (Eye切换)  │  │                    │
│              │  └────────────────┘  │                    │
│              │                      │                    │
│              │  □ 记住我    忘记密码？│                    │
│              │                      │                    │
│              │  ┌────────────────┐  │                    │
│              │  │    登  录       │  │  ← CTA 绿色       │
│              │  └────────────────┘  │    #22C55E         │
│              │                      │                    │
│              │  还没有账号？立即注册  │                    │
│              └──────────────────────┘                    │
│                Card: #1E293B                             │
│                border-radius: 12px                       │
│                max-width: 420px                          │
│                padding: 40px                             │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

- **布局**: 全屏居中单卡片，垂直水平居中（flexbox）
- **卡片宽度**: max-width 420px
- **背景**: 页面 `#0F172A`，卡片 `#1E293B`

## 组件规范

### Logo 区域
- Logo + 产品名 "Lazyday" 居中
- 产品名 font-size: 24px, font-weight: 700, color: `#F8FAFC`
- 下方副标题 "开放平台" font-size: 14px, color: `#94A3B8`

### 表单
- `<Form.Item label="用户名">` — 必须有 label
- `<Input prefix={<UserOutlined />}>` — 保留已有图标前缀
- `<Input.Password prefix={<LockOutlined />}>` — 自带 Eye 切换
- 验证: onBlur 实时校验 + onFinish 提交校验
- 错误: 内联红色提示 + `aria-live="polite"`

### 记住我 + 忘记密码
- 水平排列，Checkbox 左 / Link 右
- "记住我" 勾选后传 `remember: true`，Backend 签发 30d Token

### 登录按钮
- **Ant Design `<Button type="primary">`**，通过 ConfigProvider 设 `colorPrimary: #22C55E`
- 或者直接用 `style={{ background: '#22C55E' }}`
- `loading={loading}` 防重复提交
- 全宽 `block`，size `large`

### 底部链接
- "还没有账号？" color: `#94A3B8` + "立即注册" color: `#3B82F6` (Info)
- `<Link href="/login">` → `<Link href="/register">`

## 交互流程

```
用户输入 → 点击登录
  ├── Button loading=true, disabled
  ├── POST /api/portal/v1/auth/login
  ├── 成功 → message.success('登录成功') → router.push('/overview')
  │         → setUser(response.data) 更新 Zustand
  └── 失败 → message.error(error.message)
           → Button loading=false
           → 聚焦到第一个错误字段
```

## 错误状态

| 场景 | 展示 |
|------|------|
| 用户名/密码空 | 内联红色 "请输入用户名" / "请输入密码" |
| 认证失败 401 | `message.error('用户名或密码错误')` |
| 网络错误 | `message.error('网络异常，请稍后重试')` |
| 账户被禁用 | `message.error('账户已被暂停，请联系管理员')` |

## 可访问性要点

- Tab 顺序: 用户名 → 密码 → 记住我 → 忘记密码 → 登录 → 注册
- Enter 键在密码框触发登录
- 错误信息 `role="alert"` 供屏幕阅读器播报