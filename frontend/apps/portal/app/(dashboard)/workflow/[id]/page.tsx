'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function WorkflowDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <>
      <PageHeader title="Workflow 编辑器" subtitle="可视化流程编排画布" />
      <Card variant="borderless" style={{ minHeight: 400 }}>
        <Empty description="Workflow 编辑器（ReactFlow）将在 Phase 4 实现后可用" />
      </Card>
    </>
  );
}
