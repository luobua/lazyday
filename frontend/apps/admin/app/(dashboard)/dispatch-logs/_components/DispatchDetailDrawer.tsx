'use client';

import { Alert, Button, Descriptions, Drawer, Space, Typography } from 'antd';
import type { DispatchLog } from '@lazyday/types';
import { formatTimestamp } from '@lazyday/utils';
import { StatusTag } from './StatusTag';

export function DispatchDetailDrawer({
  open,
  loading,
  log,
  onClose,
}: {
  open: boolean;
  loading?: boolean;
  log?: DispatchLog;
  onClose: () => void;
}) {
  const payloadText = log ? JSON.stringify(log.payload ?? null, null, 2) : '';

  return (
    <Drawer title="下发详情" placement="right" width={560} open={open} onClose={onClose} loading={loading}>
      {log && (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="msgId">{log.msgId}</Descriptions.Item>
            <Descriptions.Item label="tenantId">{log.tenantId}</Descriptions.Item>
            <Descriptions.Item label="type">{log.type}</Descriptions.Item>
            <Descriptions.Item label="status"><StatusTag status={log.status} /></Descriptions.Item>
            <Descriptions.Item label="createdTime">{formatTimestamp(log.createdTime)}</Descriptions.Item>
            <Descriptions.Item label="ackedTime">{log.ackedTime ? formatTimestamp(log.ackedTime) : '-'}</Descriptions.Item>
          </Descriptions>
          <div>
            <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }}>
              <Typography.Title level={5} style={{ margin: 0 }}>Payload</Typography.Title>
              <Button size="small" onClick={() => navigator.clipboard?.writeText(payloadText)}>复制 JSON</Button>
            </Space>
            <pre style={{ maxHeight: 360, overflow: 'auto', padding: 12, background: '#f5f5f5', borderRadius: 6 }}>
              {payloadText}
            </pre>
          </div>
          {log.lastError && <Alert type="error" message={log.lastError} />}
        </Space>
      )}
    </Drawer>
  );
}
