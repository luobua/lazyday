'use client';

import React from 'react';
import { Card, Col, Row, Statistic, Typography, Table, Tag, Empty } from 'antd';
import {
  ApiOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { PageHeader } from '@lazyday/ui';
import type { CallLogItem } from '@lazyday/types';
import { formatTimestamp, formatLatency } from '@lazyday/utils';

const { Text } = Typography;

// Mock 数据（Backend 就绪后替换为 TanStack Query）
const mockStats = {
  todayCalls: 12847,
  successRate: 98.6,
  avgLatency: 45,
  activeKeys: 5,
};

const mockRecentLogs: CallLogItem[] = [
  { id: 1, tenant_id: 1, app_key: 'ak_demo001', path: '/api/open/v1/ai/chat', method: 'POST', status_code: 200, latency_ms: 342, request_id: 'req_001', created_at: '2026-04-26T10:20:00Z' },
  { id: 2, tenant_id: 1, app_key: 'ak_demo001', path: '/api/open/v1/ai/tts', method: 'POST', status_code: 200, latency_ms: 1205, request_id: 'req_002', created_at: '2026-04-26T10:19:30Z' },
  { id: 3, tenant_id: 1, app_key: 'ak_demo002', path: '/api/open/v1/ai/chat', method: 'POST', status_code: 429, latency_ms: 12, request_id: 'req_003', created_at: '2026-04-26T10:19:15Z' },
  { id: 4, tenant_id: 1, app_key: 'ak_demo001', path: '/api/open/v1/ai/chat', method: 'POST', status_code: 200, latency_ms: 289, request_id: 'req_004', created_at: '2026-04-26T10:18:45Z' },
  { id: 5, tenant_id: 1, app_key: 'ak_demo002', path: '/api/open/v1/rag/search', method: 'POST', status_code: 500, latency_ms: 3200, request_id: 'req_005', created_at: '2026-04-26T10:18:20Z' },
];

export default function OverviewPage() {
  const logColumns = [
    {
      title: '接口路径',
      dataIndex: 'path',
      key: 'path',
      render: (path: string) => <Text code>{path}</Text>,
    },
    {
      title: 'AppKey',
      dataIndex: 'app_key',
      key: 'app_key',
    },
    {
      title: '状态码',
      dataIndex: 'status_code',
      key: 'status_code',
      width: 100,
      render: (code: number) => {
        const color = code >= 200 && code < 300 ? 'success' : code >= 400 ? 'error' : 'warning';
        return <Tag color={color}>{code}</Tag>;
      },
    },
    {
      title: '延迟',
      dataIndex: 'latency_ms',
      key: 'latency_ms',
      width: 100,
      render: (ms: number) => (
        <Text type={ms > 1000 ? 'warning' : undefined}>
          {formatLatency(ms)}
        </Text>
      ),
    },
    {
      title: '时间',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 180,
      render: (t: string) => formatTimestamp(t),
    },
  ];

  return (
    <>
      <PageHeader title="控制台概览" subtitle="查看调用量、成功率和最近日志" />
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" variant="borderless">
            <Statistic
              title="今日调用量"
              value={mockStats.todayCalls}
              prefix={<ApiOutlined />}
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" variant="borderless">
            <Statistic
              title="成功率"
              value={mockStats.successRate}
              precision={1}
              suffix="%"
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" variant="borderless">
            <Statistic
              title="平均延迟"
              value={mockStats.avgLatency}
              suffix="ms"
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" variant="borderless">
            <Statistic
              title="活跃 AppKey"
              value={mockStats.activeKeys}
              prefix={<ThunderboltOutlined />}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
      </Row>

      <Card title="最近调用日志" variant="borderless" style={{ marginBottom: 24 }}>
        <Table
          dataSource={mockRecentLogs}
          columns={logColumns}
          rowKey="id"
          pagination={false}
          size="small"
          locale={{ emptyText: <Empty description="暂无调用记录" /> }}
        />
      </Card>
    </>
  );
}
