import { NextRequest, NextResponse } from 'next/server';

const PORTAL_PUBLIC_PATHS = ['/login', '/register', '/forgot-password'];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Skip static resources and API routes
  if (
    pathname.includes('/_next') ||
    pathname.includes('/api') ||
    pathname.endsWith('.ico') ||
    pathname.endsWith('.png') ||
    pathname.endsWith('.svg')
  ) {
    return NextResponse.next();
  }

  // Portal public paths
  if (PORTAL_PUBLIC_PATHS.some((p) => pathname.startsWith(p))) {
    return NextResponse.next();
  }

  // Root redirect
  if (pathname === '/') {
    const token = request.cookies.get('access_token')?.value;
    if (token) {
      return NextResponse.redirect(new URL('/overview', request.url));
    }
    return NextResponse.redirect(new URL('/login', request.url));
  }

  // Portal protected paths
  const token = request.cookies.get('access_token')?.value;
  if (!token) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
};
