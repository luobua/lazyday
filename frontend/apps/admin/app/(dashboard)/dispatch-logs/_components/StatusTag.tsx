'use client';

import { Tag } from 'antd';
import type { DispatchStatus } from '@lazyday/types';

export const dispatchStatusTagProps: Record<DispatchStatus, { color: string; label: string }> = {
  pending: { color: 'default', label: '等待发送' },
  sent: { color: 'processing', label: '已发送' },
  acked: { color: 'success', label: '已确认' },
  failed: { color: 'error', label: '失败' },
  timeout: { color: 'warning', label: '超时' },
};

export function StatusTag({ status }: { status: DispatchStatus }) {
  const props = dispatchStatusTagProps[status] ?? { color: 'default', label: status };
  return <Tag color={props.color}>{props.label}</Tag>;
}
