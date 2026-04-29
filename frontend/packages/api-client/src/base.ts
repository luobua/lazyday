import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse } from '@lazyday/types';

// CSRF token cache
let csrfToken: string | null = null;
let csrfFetching: Promise<string> | null = null;

async function ensureCsrfToken(baseURL: string, csrfPath: string): Promise<string> {
  if (csrfToken) return csrfToken;
  if (csrfFetching) return csrfFetching;

  csrfFetching = axios
    .get<ApiResponse<{ token: string }>>(`${baseURL}${csrfPath}`, {
      withCredentials: true,
    })
    .then((res) => {
      csrfToken = res.data.data.token;
      csrfFetching = null;
      return csrfToken;
    })
    .catch(() => {
      csrfFetching = null;
      return '';
    });

  return csrfFetching;
}

const STATE_CHANGING_METHODS = ['post', 'put', 'delete', 'patch'];

/**
 * 创建 Axios 实例
 * - baseURL 指向 Edge Gateway（开发环境直连 Backend）
 * - withCredentials: true 自动携带 HTTP-Only Cookie
 */
function createClient(baseURL: string, csrfPath: string) {
  const client = axios.create({
    baseURL,
    timeout: 15_000,
    withCredentials: true,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  // 请求拦截器：自动注入 CSRF token
  client.interceptors.request.use(
    async (config: InternalAxiosRequestConfig) => {
      const method = (config.method || '').toLowerCase();
      if (STATE_CHANGING_METHODS.includes(method)) {
        const url = config.url || '';
        const isAuthExempt = url.includes('/auth/login') || url.includes('/auth/register');
        if (!isAuthExempt) {
          const token = await ensureCsrfToken(baseURL, csrfPath);
          if (token) {
            config.headers.set('X-CSRF-Token', token);
          }
        }
      }
      return config;
    },
    (error) => Promise.reject(error),
  );

  // 响应拦截器：统一错误处理
  client.interceptors.response.use(
    (response) => response,
    (error: AxiosError<ApiResponse>) => {
      const status = error.response?.status;

      if (status === 401) {
        // 清除 Cookie 并跳转登录
        document.cookie = 'access_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
        document.cookie = 'refresh_token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';

        // 根据当前路径跳转到对应登录页
        const path = window.location.pathname;
        if (path.startsWith('/admin')) {
          window.location.href = '/admin/login';
        } else {
          window.location.href = '/login';
        }
        return Promise.reject(new Error('认证已过期，请重新登录'));
      }

      // 提取后端错误信息
      const message = error.response?.data?.message || error.message || '请求失败';
      const errorCode = error.response?.data?.error_code;

      return Promise.reject(
        Object.assign(new Error(message), {
          status,
          error_code: errorCode,
          response: error.response?.data,
        }),
      );
    },
  );

  return client;
}

// Portal 客户端（指向 Backend 的 Portal API）
export const portalClient = createClient(
  process.env.NEXT_PUBLIC_PORTAL_API_URL || 'http://localhost:8080',
  '/api/portal/v1/auth/csrf-token',
);

// Admin 客户端（指向 Backend 的 Admin API）
export const adminClient = createClient(
  process.env.NEXT_PUBLIC_ADMIN_API_URL || 'http://localhost:8080',
  '/api/admin/v1/auth/csrf-token',
);

/**
 * 类型安全的 GET 请求
 */
export async function get<T>(client: ReturnType<typeof createClient>, url: string, params?: Record<string, unknown>): Promise<ApiResponse<T>> {
  const res = await client.get<ApiResponse<T>>(url, { params });
  return res.data;
}

/**
 * 类型安全的 POST 请求
 */
export async function post<T>(client: ReturnType<typeof createClient>, url: string, data?: unknown): Promise<ApiResponse<T>> {
  const res = await client.post<ApiResponse<T>>(url, data);
  return res.data;
}

/**
 * 类型安全的 PUT 请求
 */
export async function put<T>(client: ReturnType<typeof createClient>, url: string, data?: unknown): Promise<ApiResponse<T>> {
  const res = await client.put<ApiResponse<T>>(url, data);
  return res.data;
}

/**
 * 类型安全的 DELETE 请求
 */
export async function del<T>(client: ReturnType<typeof createClient>, url: string): Promise<ApiResponse<T>> {
  const res = await client.delete<ApiResponse<T>>(url);
  return res.data;
}
