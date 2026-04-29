'use client';

import { useQuery } from '@tanstack/react-query';
import { adminTenantApi } from '@lazyday/api-client';

export function useAdminOverview() {
  return useQuery({
    queryKey: ['admin', 'overview'],
    queryFn: async () => {
      const res = await adminTenantApi.overview();
      return res.data;
    },
  });
}
