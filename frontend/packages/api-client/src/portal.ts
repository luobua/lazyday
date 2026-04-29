import { portalClient, get, post, put, del } from './base';
import type {
  AppKeyInfo,
  CreateAppKeyRequest,
  CreateAppKeyResponse,
  CallLogItem,
  CallLogQuery,
  CallLogStats,
  QuotaInfo,
  PageResponse,
  UserInfo,
  LoginRequest,
  RegisterRequest,
  TenantInfo,
  WebhookConfig,
  CreateWebhookRequest,
  RagConfig,
  AgentConfig,
  WorkflowConfig,
} from '@lazyday/types';

// ========== Auth API ==========

export const authApi = {
  login: (data: LoginRequest) =>
    post<UserInfo>(portalClient, '/api/portal/v1/auth/login', data),

  register: (data: RegisterRequest) =>
    post<UserInfo>(portalClient, '/api/portal/v1/auth/register', data),

  logout: () =>
    post<void>(portalClient, '/api/portal/v1/auth/logout'),

  me: () =>
    get<UserInfo>(portalClient, '/api/portal/v1/auth/me'),

  refreshToken: () =>
    post<{ access_token: string }>(portalClient, '/api/portal/v1/auth/refresh'),
};

// ========== Tenant API ==========

export const tenantApi = {
  info: () =>
    get<TenantInfo>(portalClient, '/api/portal/v1/tenant'),

  update: (data: Partial<TenantInfo>) =>
    put<TenantInfo>(portalClient, '/api/portal/v1/tenant', data),
};

// ========== Credentials API ==========

export const credentialsApi = {
  list: () =>
    get<AppKeyInfo[]>(portalClient, '/api/portal/v1/credentials'),

  create: (data: CreateAppKeyRequest) =>
    post<CreateAppKeyResponse>(portalClient, '/api/portal/v1/credentials', data),

  detail: (id: number) =>
    get<AppKeyInfo>(portalClient, `/api/portal/v1/credentials/${id}`),

  disable: (id: number) =>
    put<void>(portalClient, `/api/portal/v1/credentials/${id}/disable`),

  enable: (id: number) =>
    put<void>(portalClient, `/api/portal/v1/credentials/${id}/enable`),

  rotateSecret: (id: number) =>
    post<{ secret_key: string }>(portalClient, `/api/portal/v1/credentials/${id}/rotate-secret`),

  delete: (id: number) =>
    del<void>(portalClient, `/api/portal/v1/credentials/${id}`),
};

// ========== Call Logs API ==========

export const callLogsApi = {
  list: (query: CallLogQuery) =>
    get<PageResponse<CallLogItem>>(portalClient, '/api/portal/v1/logs', query as Record<string, unknown>),

  stats: (params?: Pick<CallLogQuery, 'start_time' | 'end_time'>) =>
    get<CallLogStats>(portalClient, '/api/portal/v1/logs/stats', params as Record<string, unknown>),

  exportCsv: (query: CallLogQuery) =>
    portalClient.get('/api/portal/v1/logs/export', {
      params: query,
      responseType: 'blob',
    }),
};

// ========== Quota API ==========

export const quotaApi = {
  info: () =>
    get<QuotaInfo>(portalClient, '/api/portal/v1/quota'),
};

// ========== Webhook API ==========

export const webhookApi = {
  list: () =>
    get<WebhookConfig[]>(portalClient, '/api/portal/v1/webhooks'),

  create: (data: CreateWebhookRequest) =>
    post<WebhookConfig>(portalClient, '/api/portal/v1/webhooks', data),

  update: (id: number, data: Partial<CreateWebhookRequest>) =>
    put<WebhookConfig>(portalClient, `/api/portal/v1/webhooks/${id}`, data),

  delete: (id: number) =>
    del<void>(portalClient, `/api/portal/v1/webhooks/${id}`),

  test: (id: number) =>
    post<{ status: number; body: string }>(portalClient, `/api/portal/v1/webhooks/${id}/test`),
};

// ========== RAG API ==========

export const ragApi = {
  list: () =>
    get<RagConfig[]>(portalClient, '/api/portal/v1/rag'),

  create: (data: Partial<RagConfig>) =>
    post<RagConfig>(portalClient, '/api/portal/v1/rag', data),

  detail: (id: number) =>
    get<RagConfig>(portalClient, `/api/portal/v1/rag/${id}`),

  update: (id: number, data: Partial<RagConfig>) =>
    put<RagConfig>(portalClient, `/api/portal/v1/rag/${id}`, data),

  delete: (id: number) =>
    del<void>(portalClient, `/api/portal/v1/rag/${id}`),

  search: (id: number, query: string, topK?: number) =>
    post<Array<{ chunk: string; score: number; title: string }>>(
      portalClient,
      `/api/portal/v1/rag/${id}/search`,
      { query, top_k: topK },
    ),
};

// ========== Agent API ==========

export const agentApi = {
  list: () =>
    get<AgentConfig[]>(portalClient, '/api/portal/v1/agent'),

  create: (data: Partial<AgentConfig>) =>
    post<AgentConfig>(portalClient, '/api/portal/v1/agent', data),

  detail: (id: number) =>
    get<AgentConfig>(portalClient, `/api/portal/v1/agent/${id}`),

  update: (id: number, data: Partial<AgentConfig>) =>
    put<AgentConfig>(portalClient, `/api/portal/v1/agent/${id}`, data),

  delete: (id: number) =>
    del<void>(portalClient, `/api/portal/v1/agent/${id}`),

  debug: (id: number, message: string) =>
    post<{ response: string }>(portalClient, `/api/portal/v1/agent/${id}/debug`, { message }),
};

// ========== Workflow API ==========

export const workflowApi = {
  list: () =>
    get<WorkflowConfig[]>(portalClient, '/api/portal/v1/workflow'),

  create: (data: Partial<WorkflowConfig>) =>
    post<WorkflowConfig>(portalClient, '/api/portal/v1/workflow', data),

  detail: (id: number) =>
    get<WorkflowConfig>(portalClient, `/api/portal/v1/workflow/${id}`),

  update: (id: number, data: Partial<WorkflowConfig>) =>
    put<WorkflowConfig>(portalClient, `/api/portal/v1/workflow/${id}`, data),

  delete: (id: number) =>
    del<void>(portalClient, `/api/portal/v1/workflow/${id}`),

  publish: (id: number) =>
    post<{ version: number }>(portalClient, `/api/portal/v1/workflow/${id}/publish`),

  run: (id: number, params: Record<string, unknown>) =>
    post<Record<string, unknown>>(portalClient, `/api/portal/v1/workflow/${id}/run`, params),

  versions: (id: number) =>
    get<Array<{ version: number; created_at: string }>>(portalClient, `/api/portal/v1/workflow/${id}/versions`),

  rollback: (id: number, version: number) =>
    post<void>(portalClient, `/api/portal/v1/workflow/${id}/rollback`, { version }),
};
