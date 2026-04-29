'use client';

import { useQuery } from '@tanstack/react-query';
import { quotaApi } from '@lazyday/api-client';

export function useQuotaUsage() {
  return useQuery({
    queryKey: ['quota-usage'],
    queryFn: async () => {
      const res = await quotaApi.info();
      return res.data;
    },
  });
}
