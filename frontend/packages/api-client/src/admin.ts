import { adminClient, get, post, put, del } from './base';
import type {
  ApiResponse,
  UserInfo,
  LoginRequest,
  TenantInfo,
  CallLogItem,
  CallLogQuery,
  CallLogStats,
  PageResponse,
  QuotaInfo,
  QuotaPlan,
  CreateQuotaPlanRequest,
  UpdateQuotaPlanRequest,
  OverrideQuotaRequest,
  RagConfig,
  AgentConfig,
  WorkflowConfig,
  AdminTenantSummary,
  AdminTenantDetail,
  AdminOverviewMetrics,
  DispatchLog,
  DispatchLogQuery,
  EdgeStatus,
} from '@lazyday/types';

// ========== Admin Auth API ==========

export const adminAuthApi = {
  login: (data: LoginRequest) =>
    post<UserInfo>(adminClient, '/api/admin/v1/auth/login', data),

  logout: () =>
    post<void>(adminClient, '/api/admin/v1/auth/logout'),

  me: () =>
    get<UserInfo>(adminClient, '/api/admin/v1/auth/me'),
};

// ========== Admin Tenant API ==========

export const adminTenantApi = {
  list: (params?: { page?: number; size?: number; keyword?: string; status?: string }) =>
    get<PageResponse<AdminTenantSummary>>(adminClient, '/api/admin/v1/tenants', params as Record<string, unknown>),

  detail: (id: number) =>
    get<AdminTenantDetail>(adminClient, `/api/admin/v1/tenants/${id}`),

  suspend: (id: number) =>
    post<AdminTenantSummary>(adminClient, `/api/admin/v1/tenants/${id}/suspend`),

  resume: (id: number) =>
    post<AdminTenantSummary>(adminClient, `/api/admin/v1/tenants/${id}/resume`),

  updateQuota: (id: number, data: OverrideQuotaRequest) =>
    put<QuotaInfo>(adminClient, `/api/admin/v1/tenants/${id}/quota`, data),

  overview: () =>
    get<AdminOverviewMetrics>(adminClient, '/api/admin/v1/overview'),
};

// ========== Admin Plan API ==========

export const adminPlanApi = {
  list: () =>
    get<QuotaPlan[]>(adminClient, '/api/admin/v1/plans'),

  create: (data: CreateQuotaPlanRequest) =>
    post<QuotaPlan>(adminClient, '/api/admin/v1/plans', data),

  update: (id: number, data: UpdateQuotaPlanRequest) =>
    put<QuotaPlan>(adminClient, `/api/admin/v1/plans/${id}`, data),

  delete: (id: number) =>
    del<void>(adminClient, `/api/admin/v1/plans/${id}`),
};

// ========== Admin Call Logs API ==========

export const adminCallLogsApi = {
  list: (query: CallLogQuery) =>
    get<PageResponse<CallLogItem>>(adminClient, '/api/admin/v1/call-logs', query as Record<string, unknown>),

  stats: (params?: Record<string, unknown>) =>
    get<CallLogStats>(adminClient, '/api/admin/v1/call-logs/stats', params),
};

// ========== Admin RAG API ==========

export const adminRagApi = {
  list: (params?: { tenant_id?: number }) =>
    get<RagConfig[]>(adminClient, '/api/admin/v1/rag', params as Record<string, unknown>),
};

// ========== Admin Agent API ==========

export const adminAgentApi = {
  list: (params?: { tenant_id?: number }) =>
    get<AgentConfig[]>(adminClient, '/api/admin/v1/agent', params as Record<string, unknown>),
};

// ========== Admin Workflow API ==========

export const adminWorkflowApi = {
  list: (params?: { tenant_id?: number }) =>
    get<WorkflowConfig[]>(adminClient, '/api/admin/v1/workflow', params as Record<string, unknown>),
};

// ========== Admin Brain Config API ==========

export const adminBrainConfigApi = {
  list: (params?: { tenant_id?: number; status?: string }) =>
    get<PageResponse<{
      tenant_id: number;
      tenant_name: string;
      config_version: string;
      status: 'pending' | 'dispatched' | 'failed';
      dispatched_at: string;
      acked_at: string;
      error_reason?: string;
      request_id: string;
    }>>(adminClient, '/api/admin/v1/brain-configs', params as Record<string, unknown>),

  dispatch: (tenantId: number) =>
    post<{ request_id: string }>(adminClient, `/api/admin/v1/brain-configs/${tenantId}/dispatch`),
};

// ========== Internal Dispatch API ==========

export const dispatchApi = {
  listLogs: (params?: DispatchLogQuery) =>
    localGet<PageResponse<DispatchLog>>('/api/internal/dispatch/logs', params as Record<string, unknown>),

  getLog: (msgId: string) =>
    localGet<DispatchLog>(`/api/internal/dispatch/logs/${msgId}`),

  postDispatch: (tenantId: number, payload: unknown) =>
    localPost<{ msgId: string }>(`/api/internal/dispatch/${tenantId}`, payload),

  getEdgeStatus: () =>
    localGet<EdgeStatus>('/api/internal/edge/status'),
};

async function localGet<T>(path: string, params?: Record<string, unknown>): Promise<ApiResponse<T>> {
  const query = new URLSearchParams();
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    if (Array.isArray(value)) {
      value.forEach((item) => query.append(key, String(item)));
    } else {
      query.set(key, String(value));
    }
  });
  const response = await fetch(`${adminBasePath()}${path}${query.size ? `?${query.toString()}` : ''}`, {
    credentials: 'include',
    cache: 'no-store',
  });
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.json();
}

async function localPost<T>(path: string, payload: unknown): Promise<ApiResponse<T>> {
  const response = await fetch(`${adminBasePath()}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.json();
}

function adminBasePath() {
  return process.env.NEXT_PUBLIC_ADMIN_BASE_PATH || '/admin';
}
