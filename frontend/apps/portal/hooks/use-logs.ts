'use client';

import { useMutation, useQuery } from '@tanstack/react-query';
import { callLogsApi } from '@lazyday/api-client';
import type { CallLogQuery } from '@lazyday/types';

export function useCallLogs(query: CallLogQuery) {
  return useQuery({
    queryKey: ['call-logs', query],
    queryFn: async () => {
      const res = await callLogsApi.list(query);
      return res.data;
    },
  });
}

export function useCallLogStats(params?: Pick<CallLogQuery, 'start_time' | 'end_time'>) {
  return useQuery({
    queryKey: ['call-log-stats', params],
    queryFn: async () => {
      const res = await callLogsApi.stats(params);
      return res.data;
    },
  });
}

export function useExportCallLogs() {
  return useMutation({
    mutationFn: async (query: CallLogQuery) => {
      const response = await callLogsApi.exportCsv(query);
      return response.data as Blob;
    },
  });
}
