'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function AdminAgentPage() {
  return (
    <>
      <PageHeader title="Agent 管理" subtitle="全平台 Agent 配置管理" />
      <Card variant="borderless">
        <Empty description="Agent 管理功能将在 Phase 4 实现后可用" />
      </Card>
    </>
  );
}
