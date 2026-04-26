'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function WorkflowListPage() {
  return (
    <>
      <PageHeader title="Workflow 编排" subtitle="可视化流程编排和管理" />
      <Card variant="borderless">
        <Empty description="Workflow 编排功能将在 Phase 4 实现后可用" />
      </Card>
    </>
  );
}
