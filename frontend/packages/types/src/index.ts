// 通用 API 响应类型

export interface ApiResponse<T = unknown> {
  code: number;
  error_code?: string;
  message: string;
  data: T;
  request_id: string;
}

// 分页请求
export interface PageRequest {
  page: number;
  size: number;
}

// 分页响应
export interface PageResponse<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
  total_pages: number;
}

// 用户相关
export interface UserInfo {
  id: number;
  username: string;
  email: string;
  role: 'tenant' | 'admin';
  tenant_id?: number;
  created_at: string;
  updated_at: string;
}

export interface LoginRequest {
  username: string;
  password: string;
  remember?: boolean;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  tenantName: string;
}

// 租户相关
export interface TenantInfo {
  id: number;
  name: string;
  status: 'active' | 'suspended' | 'deleted';
  plan_id?: number;
  plan_name?: string;
  created_at: string;
  updated_at: string;
}

// AppKey 相关
export interface AppKeyInfo {
  id: number;
  name: string;
  app_key: string;
  secret_key_masked: string;
  status: 'active' | 'disabled' | 'deleted';
  scopes: string[];
  created_at: string;
  updated_at: string;
  secret_key_rotated_at?: string;
}

export interface CreateAppKeyRequest {
  name: string;
  scopes?: string[];
}

export interface CreateAppKeyResponse {
  id: number;
  name: string;
  app_key: string;
  secret_key: string; // 仅创建时返回一次
  scopes: string[];
}

// 调用日志
export interface CallLogItem {
  id: number;
  tenant_id: number;
  app_key: string;
  path: string;
  method: string;
  status_code: number;
  latency_ms: number;
  request_id: string;
  created_at: string;
}

export interface CallLogQuery {
  start_time?: string;
  end_time?: string;
  path?: string;
  status_code?: number;
  app_key?: string;
  page?: number;
  size?: number;
}

// 调用统计
export interface CallStats {
  date: string;
  total: number;
  success: number;
  failed: number;
  avg_latency_ms: number;
}

// Webhook
export interface WebhookConfig {
  id: number;
  name: string;
  url: string;
  events: string[];
  secret: string;
  status: 'active' | 'disabled';
  created_at: string;
  updated_at: string;
}

export interface CreateWebhookRequest {
  name: string;
  url: string;
  events: string[];
}

// 配额
export interface QuotaInfo {
  plan_name: string;
  qps_limit: number;
  monthly_limit: number;
  monthly_used: number;
  daily_used: number;
}

// RAG
export interface RagConfig {
  id: number;
  name: string;
  description?: string;
  embedding_model: string;
  dimension: number;
  top_k: number;
  similarity_threshold: number;
  document_count: number;
  status: 'active' | 'processing' | 'failed';
  created_at: string;
  updated_at: string;
}

// Agent
export interface AgentConfig {
  id: number;
  name: string;
  description?: string;
  system_prompt: string;
  model: string;
  temperature: number;
  max_tokens: number;
  tools: string[];
  tts_enabled: boolean;
  asr_enabled: boolean;
  rag_id?: number;
  status: 'active' | 'disabled';
  created_at: string;
  updated_at: string;
}

// Workflow
export interface WorkflowConfig {
  id: number;
  name: string;
  description?: string;
  version: number;
  status: 'draft' | 'published';
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  created_at: string;
  updated_at: string;
}

export interface WorkflowNode {
  id: string;
  type: 'start' | 'end' | 'llm' | 'rag' | 'tool' | 'condition' | 'tts' | 'asr';
  position: { x: number; y: number };
  data: Record<string, unknown>;
}

export interface WorkflowEdge {
  id: string;
  source: string;
  target: string;
  source_handle?: string;
  label?: string;
}
