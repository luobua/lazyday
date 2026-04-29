'use client';

import React from 'react';
import { Card, Col, Row, Statistic, Table } from 'antd';
import { ApiOutlined, BarChartOutlined, CheckCircleOutlined, CloudOutlined, TeamOutlined } from '@ant-design/icons';
import { PageHeader } from '@lazyday/ui';
import { formatNumber } from '@lazyday/utils';
import { useAdminOverview } from '@/hooks/use-admin-overview';

export default function OverviewPage() {
  const { data, isLoading } = useAdminOverview();

  return (
    <>
      <PageHeader title="系统概览" subtitle="平台运营数据大盘" />
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card variant="borderless">
            <Statistic title="总租户数" value={data?.total_tenants ?? 0} prefix={<TeamOutlined />} loading={isLoading} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card variant="borderless">
            <Statistic title="7 日活跃租户" value={data?.active_tenants_7d ?? 0} prefix={<CheckCircleOutlined />} loading={isLoading} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card variant="borderless">
            <Statistic title="今日调用量" value={data?.today_calls ?? 0} formatter={(value) => formatNumber(Number(value))} prefix={<ApiOutlined />} loading={isLoading} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card variant="borderless">
            <Statistic
              title="今日成功率"
              value={data?.today_success_rate == null ? '-' : data.today_success_rate * 100}
              precision={2}
              suffix={data?.today_success_rate == null ? undefined : '%'}
              prefix={<CloudOutlined />}
              loading={isLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card variant="borderless">
            <Statistic
              title="今日活跃接口"
              value={data?.top_paths_today?.length ?? 0}
              prefix={<BarChartOutlined />}
              loading={isLoading}
            />
          </Card>
        </Col>
      </Row>
      <Card title="今日 Top10 接口" variant="borderless">
        <Table
          rowKey="path"
          dataSource={data?.top_paths_today ?? []}
          loading={isLoading}
          pagination={false}
          columns={[
            { title: '路径', dataIndex: 'path', key: 'path' },
            { title: '调用量', dataIndex: 'call_count', key: 'call_count', width: 160, render: (value: number) => formatNumber(value) },
          ]}
        />
      </Card>
    </>
  );
}
