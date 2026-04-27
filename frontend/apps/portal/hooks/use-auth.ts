import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi } from '@lazyday/api-client';
import { useAuthStore } from '@/store/auth';
import type { LoginRequest, RegisterRequest } from '@lazyday/types';

export function useMe() {
  const setUser = useAuthStore((s) => s.setUser);
  return useQuery({
    queryKey: ['auth', 'me'],
    queryFn: async () => {
      const res = await authApi.me();
      if (res.code === 0 && res.data) {
        setUser(res.data);
      }
      return res.data;
    },
    retry: false,
  });
}

export function useLogin() {
  const setUser = useAuthStore((s) => s.setUser);
  return useMutation({
    mutationFn: async (data: LoginRequest) => {
      const res = await authApi.login(data);
      if (res.code === 0 && res.data) {
        setUser(res.data);
      }
      return res;
    },
  });
}

export function useRegister() {
  const setUser = useAuthStore((s) => s.setUser);
  return useMutation({
    mutationFn: async (data: RegisterRequest) => {
      const res = await authApi.register(data);
      if (res.code === 0 && res.data) {
        setUser(res.data);
      }
      return res;
    },
  });
}

export function useLogout() {
  const logout = useAuthStore((s) => s.logout);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await authApi.logout();
    },
    onSuccess: () => {
      logout();
      queryClient.clear();
    },
  });
}
