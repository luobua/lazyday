'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function AdminWorkflowPage() {
  return (
    <>
      <PageHeader title="Workflow 管理" subtitle="全平台 Workflow 管理" />
      <Card variant="borderless">
        <Empty description="Workflow 管理功能将在 Phase 4 实现后可用" />
      </Card>
    </>
  );
}
