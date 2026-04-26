/**
 * Auth Layout - 登录/注册页面的无侧边栏布局
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="auth-container">
      <div className="auth-card">
        <div className="auth-logo">
          <h1>Lazyday</h1>
          <p>多租户开放平台 — 开发者控制台</p>
        </div>
        {children}
      </div>
    </div>
  );
}
