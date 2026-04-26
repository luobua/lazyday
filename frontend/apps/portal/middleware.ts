import { NextRequest, NextResponse } from 'next/server';

/**
 * Portal 路由守卫 Middleware
 *
 * JWT + HTTP-Only Cookie 认证方案：
 * - 登录成功后 Backend 设置 Set-Cookie: access_token=xxx; HttpOnly; Secure; SameSite=Strict
 * - Middleware 检查 Cookie 是否存在，未登录则重定向到登录页
 * - 公开页面（login/register/forgot-password）不拦截
 */
const PUBLIC_PATHS = ['/portal/login', '/portal/register', '/portal/forgot-password'];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // 仅拦截 Portal 路径
  if (!pathname.startsWith('/portal')) {
    return NextResponse.next();
  }

  // 排除静态资源和 API 路由
  if (
    pathname.includes('/_next') ||
    pathname.includes('/api') ||
    pathname.endsWith('.ico') ||
    pathname.endsWith('.png') ||
    pathname.endsWith('.svg')
  ) {
    return NextResponse.next();
  }

  // 公开页面放行
  if (PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    return NextResponse.next();
  }

  // 检查 access_token Cookie
  const token = request.cookies.get('access_token')?.value;

  if (!token) {
    const loginUrl = new URL('/portal/login', request.url);
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/portal/:path*'],
};
