'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminTenantApi } from '@lazyday/api-client';
import type { AdminTenantSummary, OverrideQuotaRequest, PageResponse } from '@lazyday/types';

export interface AdminTenantQuery {
  page?: number;
  size?: number;
  keyword?: string;
  status?: string;
}

const TENANTS_KEY = ['admin', 'tenants'];

export function useAdminTenants(query: AdminTenantQuery) {
  return useQuery({
    queryKey: [...TENANTS_KEY, query],
    queryFn: async () => {
      const res = await adminTenantApi.list(query);
      return normalizeTenantPage(res.data);
    },
  });
}

function normalizeTenantPage(page: PageResponse<AdminTenantSummary>): PageResponse<AdminTenantSummary> {
  return {
    ...page,
    list: page.list ?? page.content ?? [],
    total: page.total ?? page.totalElements ?? 0,
    total_pages: page.total_pages ?? page.totalPages ?? 0,
  };
}

export function useAdminTenantDetail(id?: number) {
  return useQuery({
    queryKey: [...TENANTS_KEY, 'detail', id],
    enabled: !!id,
    queryFn: async () => {
      const res = await adminTenantApi.detail(id!);
      return res.data;
    },
  });
}

export function useSuspendTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      const res = await adminTenantApi.suspend(id);
      return res.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TENANTS_KEY }),
  });
}

export function useResumeTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      const res = await adminTenantApi.resume(id);
      return res.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TENANTS_KEY }),
  });
}

export function useUpdateTenantQuota() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, data }: { id: number; data: OverrideQuotaRequest }) => {
      const res = await adminTenantApi.updateQuota(id, data);
      return res.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TENANTS_KEY }),
  });
}
