'use client';

import React from 'react';
import { Card, Col, Row, Statistic, Tag } from 'antd';
import { TeamOutlined, ApiOutlined, CheckCircleOutlined, CloudOutlined } from '@ant-design/icons';
import { PageHeader } from '@lazyday/ui';

// Mock 数据
const mockStats = {
  totalTenants: 128,
  activeTenants: 95,
  todayCalls: 89432,
  successRate: 99.2,
};

export default function OverviewPage() {
  return (
    <>
      <PageHeader title="系统概览" subtitle="平台运营数据大盘" />
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic title="总租户数" value={mockStats.totalTenants} prefix={<TeamOutlined />} valueStyle={{ color: '#1677ff' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic title="活跃租户" value={mockStats.activeTenants} prefix={<CheckCircleOutlined />} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic title="今日调用量" value={mockStats.todayCalls} prefix={<ApiOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic title="成功率" value={mockStats.successRate} precision={1} suffix="%" prefix={<CloudOutlined />} valueStyle={{ color: '#722ed1' }} />
          </Card>
        </Col>
      </Row>
      <Card title="平台状态" variant="borderless">
        <Row gutter={16}>
          <Col span={8}>
            <Tag color="success" style={{ padding: '8px 16px', fontSize: 14 }}>Backend 服务正常</Tag>
          </Col>
          <Col span={8}>
            <Tag color="success" style={{ padding: '8px 16px', fontSize: 14 }}>Edge 网关正常</Tag>
          </Col>
          <Col span={8}>
            <Tag color="processing" style={{ padding: '8px 16px', fontSize: 14 }}>AI 大脑待配置</Tag>
          </Col>
        </Row>
      </Card>
    </>
  );
}
