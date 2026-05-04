'use client';

import { useQuery } from '@tanstack/react-query';
import { dispatchApi } from '@lazyday/api-client';

export const EDGE_STATUS_KEY = ['edgeStatus'];

export function useEdgeStatus() {
  return useQuery({
    queryKey: EDGE_STATUS_KEY,
    queryFn: async () => {
      const res = await dispatchApi.getEdgeStatus();
      return res.data;
    },
    refetchInterval: 2_000,
  });
}
