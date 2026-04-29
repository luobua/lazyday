'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { webhookApi } from '@lazyday/api-client';
import type { WebhookCreateRequest, WebhookUpdateRequest } from '@lazyday/types';

const WEBHOOKS_KEY = ['webhooks'];

export function useWebhooks() {
  return useQuery({
    queryKey: WEBHOOKS_KEY,
    queryFn: async () => {
      const res = await webhookApi.list();
      return res.data;
    },
  });
}

export function useCreateWebhook() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (data: WebhookCreateRequest) => {
      const res = await webhookApi.create(data);
      return res.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: WEBHOOKS_KEY }),
  });
}

export function useUpdateWebhook() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, data }: { id: number; data: WebhookUpdateRequest }) => {
      const res = await webhookApi.update(id, data);
      return res.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: WEBHOOKS_KEY }),
  });
}

export function useDeleteWebhook() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await webhookApi.delete(id);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: WEBHOOKS_KEY }),
  });
}

export function useRotateWebhookSecret() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      const res = await webhookApi.rotateSecret(id);
      return res.data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: WEBHOOKS_KEY }),
  });
}

export function useTestWebhook() {
  return useMutation({
    mutationFn: async (id: number) => {
      const res = await webhookApi.test(id);
      return res.data;
    },
  });
}
