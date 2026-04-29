'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminPlanApi } from '@lazyday/api-client';
import type {
  CreateQuotaPlanRequest,
  QuotaPlan,
  UpdateQuotaPlanRequest,
} from '@lazyday/types';

const PLAN_QUERY_KEY = ['admin', 'plans'];

export function useAdminPlans() {
  return useQuery({
    queryKey: PLAN_QUERY_KEY,
    queryFn: async () => {
      const res = await adminPlanApi.list();
      return res.data;
    },
  });
}

export function useCreateAdminPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (data: CreateQuotaPlanRequest) => {
      const res = await adminPlanApi.create(data);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PLAN_QUERY_KEY });
    },
  });
}

export function useUpdateAdminPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, data }: { id: number; data: UpdateQuotaPlanRequest }) => {
      const res = await adminPlanApi.update(id, data);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PLAN_QUERY_KEY });
    },
  });
}

export function useDeleteAdminPlan() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await adminPlanApi.delete(id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PLAN_QUERY_KEY });
    },
  });
}
