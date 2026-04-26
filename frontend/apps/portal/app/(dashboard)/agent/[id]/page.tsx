'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function AgentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <>
      <PageHeader title="Agent 详情" subtitle={`ID: (待加载)`} />
      <Card variant="borderless">
        <Empty description="Agent 详情页面将在 Phase 4 实现后可用" />
      </Card>
    </>
  );
}
