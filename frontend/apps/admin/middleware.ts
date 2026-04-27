import { NextRequest, NextResponse } from 'next/server';

// 有 basePath 时，Next.js matcher 已经匹配了 /admin/:path*
// pathname 是去掉 basePath 后的路径（如 /login、/overview）
const PUBLIC_PATHS = ['/login'];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // 静态资源放行
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

  // 检查登录态（cookie 名与后端一致：access_token）
  const token = request.cookies.get('access_token')?.value;

  if (!token) {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
};
