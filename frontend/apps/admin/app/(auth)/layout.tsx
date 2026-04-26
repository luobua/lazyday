/**
 * Admin Auth Layout - 独立登录页面
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="admin-auth-container">
      <div className="admin-auth-card">
        <div className="admin-auth-logo">
          <h1>Lazyday Admin</h1>
          <p>多租户开放平台 — 运营管理后台</p>
        </div>
        {children}
      </div>
    </div>
  );
}
