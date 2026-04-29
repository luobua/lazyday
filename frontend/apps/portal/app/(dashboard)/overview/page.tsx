'use client';

import React from 'react';
import { Card, Col, Empty, Row, Skeleton, Space, Statistic, Table, Tag, Typography } from 'antd';
import {
  ApiOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
  FieldTimeOutlined,
} from '@ant-design/icons';
import { PageHeader } from '@lazyday/ui';
import { formatLatency, formatNumber, formatTimestamp, getStatusCodeColor } from '@lazyday/utils';
import { useCallLogs, useCallLogStats } from '@/hooks/use-logs';
import { useCredentials } from '@/hooks/use-credentials';
import { useQuotaUsage } from '@/hooks/use-quota';

import dayjs from 'dayjs';

const { Text } = Typography;

// 默认时间范围：最近 7 天
const defaultStartTime = dayjs().subtract(7, 'day').startOf('day').toISOString();
const defaultEndTime = dayjs().endOf('day').toISOString();

export default function OverviewPage() {
  const { data: quota, isLoading: quotaLoading } = useQuotaUsage();
  const { data: stats, isLoading: statsLoading } = useCallLogStats({
    startTime: defaultStartTime,
    endTime: defaultEndTime,
  });
  const { data: logsPage, isLoading: logsLoading } = useCallLogs({
    page: 0,
    size: 5,
    startTime: defaultStartTime,
    endTime: defaultEndTime,
  });
  const { data: credentials, isLoading: credentialsLoading } = useCredentials();

  const successRate = stats?.total ? (stats.success_count / stats.total) * 100 : 0;
  const monthlyUsageRate = quota?.monthly_limit
    ? (quota.monthly_used / quota.monthly_limit) * 100
    : 0;
  const activeKeys = credentials?.filter((item) => item.status === 'ACTIVE').length ?? 0;

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
        return <Tag color={getStatusCodeColor(code)}>{code}</Tag>;
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
      dataIndex: 'request_time',
      key: 'request_time',
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
              value={quota?.daily_used ?? 0}
              prefix={<ApiOutlined />}
              valueStyle={{ color: '#1677ff' }}
              loading={quotaLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" variant="borderless">
            <Statistic
              title="成功率"
              value={successRate}
              precision={1}
              suffix="%"
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#52c41a' }}
              loading={statsLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" variant="borderless">
            <Statistic
              title="平均延迟"
              value={stats?.avg_latency_ms ?? 0}
              precision={1}
              suffix="ms"
              prefix={<ClockCircleOutlined />}
              loading={statsLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="stat-card" variant="borderless">
            <Statistic
              title="活跃 AppKey"
              value={activeKeys}
              prefix={<ThunderboltOutlined />}
              valueStyle={{ color: '#722ed1' }}
              loading={credentialsLoading}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card title="最近调用日志" variant="borderless">
            <Table
              dataSource={logsPage?.list ?? []}
              columns={logColumns}
              rowKey="id"
              pagination={false}
              size="small"
              loading={logsLoading}
              locale={{ emptyText: <Empty description="暂无调用记录" /> }}
            />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title="月度配额" variant="borderless">
            {quotaLoading ? (
              <Skeleton active paragraph={{ rows: 4 }} />
            ) : quota ? (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Statistic
                  title="套餐"
                  value={quota.plan_name}
                  prefix={<FieldTimeOutlined />}
                />
                <Statistic
                  title="月度用量"
                  value={quota.monthly_used}
                  formatter={(value) => `${formatNumber(Number(value))} / ${formatNumber(quota.monthly_limit)}`}
                />
                <Statistic
                  title="月度使用率"
                  value={monthlyUsageRate}
                  precision={1}
                  suffix="%"
                />
                <Statistic
                  title="QPS 上限"
                  value={quota.qps_limit}
                />
              </Space>
            ) : (
              <Empty description="暂无配额数据" />
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
