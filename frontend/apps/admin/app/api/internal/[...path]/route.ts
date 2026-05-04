import { NextRequest, NextResponse } from 'next/server';

const backendUrl = process.env.ADMIN_INTERNAL_BACKEND_URL || process.env.NEXT_PUBLIC_ADMIN_API_URL || 'http://localhost:8080';
const internalApiKey = process.env.ADMIN_INTERNAL_API_KEY || 'lazyday-internal-dev-key-32-chars';

export async function GET(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  return proxyInternal(request, context);
}

export async function POST(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  return proxyInternal(request, context);
}

async function proxyInternal(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const auth = await verifyAdminSession(request);
  if (!auth.ok) {
    return NextResponse.json({ code: 401, message: 'unauthorized', data: null }, { status: 401 });
  }

  const params = await context.params;
  const target = new URL(`/internal/v1/${params.path.join('/')}`, backendUrl);
  target.search = request.nextUrl.search;

  const headers = new Headers();
  headers.set('X-Internal-Api-Key', internalApiKey);
  const contentType = request.headers.get('content-type');
  if (contentType) {
    headers.set('content-type', contentType);
  }

  const response = await fetch(target, {
    method: request.method,
    headers,
    body: request.method === 'GET' ? undefined : await request.text(),
    cache: 'no-store',
  });

  return new NextResponse(response.body, {
    status: response.status,
    headers: {
      'content-type': response.headers.get('content-type') || 'application/json',
    },
  });
}

async function verifyAdminSession(request: NextRequest) {
  const cookie = request.headers.get('cookie');
  if (!cookie) {
    return { ok: false };
  }

  const response = await fetch(new URL('/api/admin/v1/auth/me', backendUrl), {
    headers: { cookie },
    cache: 'no-store',
  });

  return { ok: response.ok };
}
