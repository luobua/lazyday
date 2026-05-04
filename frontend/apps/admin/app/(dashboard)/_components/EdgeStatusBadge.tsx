'use client';

import { Skeleton, Space, Tag } from 'antd';
import { useEdgeStatus } from '@/hooks/use-edge-status';

export function EdgeStatusBadge() {
  const { data, isLoading } = useEdgeStatus();

  if (isLoading) {
    return <Skeleton.Button active size="small" style={{ width: 72, height: 16 }} />;
  }

  const connected = data?.connected ?? false;
  const agoMs = data?.lastSeenAgoMs ?? null;
  const stale = connected && agoMs !== null && agoMs > 20_000 && agoMs <= 30_000;
  const color = !connected ? 'error' : stale ? 'warning' : 'success';
  const text = !connected
    ? 'Edge 离线'
    : stale
      ? `Edge 心跳延迟 (${Math.round((agoMs ?? 0) / 1000)}s)`
      : `Edge 在线 · ${data?.sessionCount ?? 0} 个会话 · 心跳 ${Math.round((agoMs ?? 0) / 1000)}s 前`;

  return (
    <Tag color={color} aria-live="polite" style={{ marginInlineEnd: 0 }}>
      <Space size={6}><span>{text}</span></Space>
    </Tag>
  );
}
