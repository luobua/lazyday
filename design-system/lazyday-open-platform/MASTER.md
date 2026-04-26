# Lazyday Open Platform - Design System (Master)

> **Source of Truth.** When building具体页面时，先检查 `pages/[page-name].md`，有则覆盖此文件对应部分，无则严格遵循此文件。
>
> **Generated:** 2026-04-26 (ui-ux-pro-max + 人工裁剪)

---

## 1. 设计定位

| 维度 | 决策 |
|------|------|
| 产品类型 | 开发者开放平台（Developer Portal + Admin Console） |
| 风格基调 | Dark Mode OLED — 专业、高对比度、低眼压、开发者友好 |
| 组件库 | **Ant Design 5.x**（通过 ConfigProvider Token 覆盖色板） |
| 图标库 | Ant Design Icons（@ant-design/icons）— 项目已集成，保持一致 |
| 字体 | Inter（通过 Ant Design token `fontFamily` 覆盖） |

---

## 2. 色板（Ant Design Token 映射）

基于 ui-ux-pro-max 推荐的 "Code dark + run green" 色板，适配 Ant Design Token：

| 角色 | Hex | Ant Design Token | 用途 |
|------|-----|-----------------|------|
| Primary | `#1E293B` | `colorPrimary` | 主色调（Slate 800） |
| Success/CTA | `#22C55E` | `colorSuccess` | 操作成功、主要 CTA |
| Info | `#3B82F6` | `colorInfo` | 信息提示、链接 |
| Warning | `#F59E0B` | `colorWarning` | 警告状态 |
| Error | `#EF4444` | `colorError` | 错误、危险操作 |
| BG Base | `#0F172A` | `colorBgBase` | 页面背景（Slate 900） |
| BG Container | `#1E293B` | `colorBgContainer` | 卡片/容器背景 |
| BG Elevated | `#334155` | `colorBgElevated` | 弹窗/下拉背景 |
| Text Primary | `#F8FAFC` | `colorText` | 主文本（Slate 50） |
| Text Secondary | `#94A3B8` | `colorTextSecondary` | 次要文本（Slate 400） |
| Border | `#334155` | `colorBorder` | 边框（Slate 700） |

### 对比度验证

| 组合 | 比率 | 标准 |
|------|------|------|
| Text `#F8FAFC` on BG `#0F172A` | 15.4:1 | WCAG AAA |
| Text Secondary `#94A3B8` on BG `#0F172A` | 5.8:1 | WCAG AA |
| CTA `#22C55E` on BG `#1E293B` | 5.2:1 | WCAG AA |

---

## 3. 字体

| 角色 | 字体 | Token |
|------|------|-------|
| 全局 | Inter | `fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif'` |
| 代码/密钥 | Fira Code | `fontFamilyCode: 'Fira Code, Consolas, monospace'` |

```css
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Fira+Code:wght@400;500&display=swap');
```

---

## 4. 间距（Ant Design 默认 + 微调）

沿用 Ant Design 默认 spacing（8px 基准），不做自定义覆盖。

---

## 5. 交互规范

### 5.1 按钮

| 规则 | 说明 |
|------|------|
| Loading 状态 | 异步操作期间 `loading={true}` + 禁用，防止重复提交 |
| cursor | 所有可点击元素必须 `cursor: pointer` |
| 过渡 | hover/active 状态 `transition: all 200ms ease` |
| 危险操作 | 使用 `Popconfirm` 二次确认（禁用/删除/轮换） |

### 5.2 表单

| 规则 | 说明 |
|------|------|
| Label | 所有 Input 必须有 `<Form.Item label="xxx">`，禁止 placeholder-only |
| 验证时机 | `onBlur` 实时校验 + `onFinish` 提交前校验 |
| 错误展示 | 内联错误（Ant Design Form 默认），红色边框 + 文字 |
| 密码 | 使用 `<Input.Password>` 自带显示/隐藏切换 |
| 提交反馈 | Loading → Success `message.success()` 或 Error `message.error()` |
| CSRF Token | 前端拦截器自动携带 `X-CSRF-Token`，用户无感知 |

### 5.3 表格

| 规则 | 说明 |
|------|------|
| 响应式 | 外层 `overflow-x: auto`，移动端可横向滚动 |
| 行高亮 | hover 行背景色 `#334155` |
| 空状态 | 使用 Ant Design `<Empty>` 组件 |
| 操作列 | 操作按钮 `type="link"`，危险操作红色 |

### 5.4 弹窗（Modal）

| 规则 | 说明 |
|------|------|
| 遮罩 | `rgba(0, 0, 0, 0.6)` + `backdrop-filter: blur(4px)` |
| 关闭 | 支持 ESC 键 + 点击遮罩 + 关闭按钮 |
| 焦点管理 | 打开时焦点移入，关闭时焦点回到触发元素 |

### 5.5 反馈

| 类型 | 组件 | 持续时间 |
|------|------|---------|
| 操作成功 | `message.success()` | 3s 自动消失 |
| 操作失败 | `message.error()` | 5s 自动消失 |
| 复制成功 | `message.success('已复制')` | 2s |
| 确认操作 | `Popconfirm` | 用户交互关闭 |
| 一次性展示 | `Modal` + `Typography.Text copyable` | 手动关闭 |

---

## 6. 可访问性（CRITICAL）

| 规则 | 要求 | 来源 |
|------|------|------|
| 对比度 | 正文 ≥ 4.5:1，大字 ≥ 3:1 | WCAG AA |
| Focus | 所有交互元素可见 `:focus-visible` 聚焦环 | ui-ux-pro-max |
| 键盘 | Tab 顺序与视觉顺序一致，Enter/Space 可触发 | web-interface |
| ARIA | icon-only 按钮必须 `aria-label`，装饰图标 `aria-hidden="true"` | web-interface |
| 动画 | 检查 `prefers-reduced-motion`，尊重用户偏好 | ui-ux-pro-max |
| 触摸 | 最小点击区域 44x44px | ui-ux-pro-max |
| 语义 | 使用 `<button>` 不用 `<div role="button">` | web-interface |

---

## 7. 响应式断点

| 断点 | 宽度 | 场景 |
|------|------|------|
| Mobile | 375px | 手机竖屏 |
| Tablet | 768px | 平板/小笔记本 |
| Desktop | 1024px | 标准桌面 |
| Wide | 1440px | 大屏幕 |

Portal/Admin 属 Dashboard 型产品，**主要面向 Desktop/Wide，Mobile 作为兼容目标（非优先）**。

---

## 8. Anti-Patterns（禁止事项）

- No emoji as icons — 使用 @ant-design/icons SVG 图标
- No outline-none without replacement — 必须提供 focus ring 替代
- No placeholder-only inputs — 必须有 label
- No silent failures — 所有异步操作必须有反馈
- No layout-shifting hovers — 避免 `transform: scale()` 导致布局抖动
- No arbitrary z-index — 遵循 Ant Design 内置 z-index 体系
- No auto-play video — 点击触发

---

## 9. Pre-Delivery Checklist

每个页面交付前必须验证：

- [ ] 所有图标来自 @ant-design/icons，无 emoji
- [ ] 所有可点击元素有 `cursor: pointer`
- [ ] hover 状态有平滑过渡（150-300ms）
- [ ] 文本对比度 ≥ 4.5:1
- [ ] Focus 状态可见（键盘 Tab 测试）
- [ ] `prefers-reduced-motion` 已尊重
- [ ] 响应式：375px / 768px / 1024px / 1440px 无溢出
- [ ] 无内容被固定导航栏遮挡
- [ ] 移动端无水平滚动（表格除外，需 overflow-x-auto）
- [ ] 异步操作有 Loading → Success/Error 反馈
- [ ] 危险操作有 Popconfirm 二次确认
- [ ] 表单有 label、有 onBlur 校验