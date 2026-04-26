import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse } from '@lazyday/types';

/**
 * 创建 Axios 实例
 * - baseURL 指向 Edge Gateway（开发环境直连 Backend）
 * - withCredentials: true 自动携带 HTTP-Only Cookie
 */
function createClient(baseURL: string) {
  const client = axios.create({
    baseURL,
    timeout: 15_000,
    withCredentials: true,
    headers: {
      'Content-Type': 'application/json',
    },
  });

  // 请求拦截器
  client.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      // Cookie 自动携带，无需手动设置 Authorization
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
          window.location.href = '/portal/login';
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
);

// Admin 客户端（指向 Backend 的 Admin API）
export const adminClient = createClient(
  process.env.NEXT_PUBLIC_ADMIN_API_URL || 'http://localhost:8080',
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
