'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { dispatchApi } from '@lazyday/api-client';
import type { DispatchLogQuery, PageResponse, DispatchLog } from '@lazyday/types';

export const DISPATCH_LOGS_KEY = ['dispatchLogs'];

export function useDispatchLogs(query: DispatchLogQuery) {
  return useQuery({
    queryKey: [...DISPATCH_LOGS_KEY, query],
    queryFn: async () => {
      const res = await dispatchApi.listLogs(query);
      return normalizeDispatchPage(res.data);
    },
    refetchInterval: 5_000,
  });
}

export function useDispatchLog(msgId?: string) {
  return useQuery({
    queryKey: [...DISPATCH_LOGS_KEY, 'detail', msgId],
    enabled: !!msgId,
    queryFn: async () => {
      const res = await dispatchApi.getLog(msgId!);
      return res.data;
    },
  });
}

export function usePostDispatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ tenantId, payload }: { tenantId: number; payload: unknown }) => {
      const res = await dispatchApi.postDispatch(tenantId, payload);
      return res.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DISPATCH_LOGS_KEY }),
  });
}

function normalizeDispatchPage(page: PageResponse<DispatchLog>): PageResponse<DispatchLog> {
  return {
    ...page,
    list: page.list ?? page.content ?? [],
    total: page.total ?? page.totalElements ?? 0,
    total_pages: page.total_pages ?? page.totalPages ?? 0,
  };
}
