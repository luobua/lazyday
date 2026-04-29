'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminTenantApi } from '@lazyday/api-client';
import type { OverrideQuotaRequest } from '@lazyday/types';

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
      return res.data;
    },
  });
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
