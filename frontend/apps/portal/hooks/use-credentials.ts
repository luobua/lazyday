import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { credentialsApi } from '@lazyday/api-client';
import type { CreateAppKeyRequest } from '@lazyday/types';

const CREDENTIALS_KEY = ['credentials'];

export function useCredentials() {
  return useQuery({
    queryKey: CREDENTIALS_KEY,
    queryFn: async () => {
      const res = await credentialsApi.list();
      return res.data;
    },
  });
}

export function useCreateCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (data: CreateAppKeyRequest) => {
      const res = await credentialsApi.create(data);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CREDENTIALS_KEY });
    },
  });
}

export function useDisableCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await credentialsApi.disable(id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CREDENTIALS_KEY });
    },
  });
}

export function useEnableCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await credentialsApi.enable(id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CREDENTIALS_KEY });
    },
  });
}

export function useRotateSecret() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      const res = await credentialsApi.rotateSecret(id);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CREDENTIALS_KEY });
    },
  });
}

export function useDeleteCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await credentialsApi.delete(id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CREDENTIALS_KEY });
    },
  });
}
