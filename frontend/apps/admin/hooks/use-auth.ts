import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminAuthApi } from '@lazyday/api-client';
import { useAdminAuthStore } from '@/store/auth';
import type { LoginRequest } from '@lazyday/types';

export function useAdminMe() {
  const setUser = useAdminAuthStore((s) => s.setUser);
  return useQuery({
    queryKey: ['admin', 'auth', 'me'],
    queryFn: async () => {
      const res = await adminAuthApi.me();
      if (res.code === 0 && res.data) {
        setUser(res.data);
      }
      return res.data;
    },
    retry: false,
  });
}

export function useAdminLogin() {
  const setUser = useAdminAuthStore((s) => s.setUser);
  return useMutation({
    mutationFn: async (data: LoginRequest) => {
      const res = await adminAuthApi.login(data);
      if (res.code === 0 && res.data) {
        setUser(res.data);
      }
      return res;
    },
  });
}

export function useAdminLogout() {
  const logout = useAdminAuthStore((s) => s.logout);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await adminAuthApi.logout();
    },
    onSuccess: () => {
      logout();
      queryClient.clear();
    },
  });
}
