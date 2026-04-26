'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function BrainConfigsPage() {
  return (
    <>
      <PageHeader title="大脑配置" subtitle="查看租户大脑配置下发状态" />
      <Card variant="borderless">
        <Empty description="大脑配置管理功能将在 Phase 4 实现后可用" />
      </Card>
    </>
  );
}
