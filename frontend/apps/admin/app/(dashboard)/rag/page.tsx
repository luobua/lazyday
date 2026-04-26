'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function AdminRagPage() {
  return (
    <>
      <PageHeader title="RAG 管理" subtitle="全平台知识库管理" />
      <Card variant="borderless">
        <Empty description="RAG 管理功能将在 Phase 4 实现后可用" />
      </Card>
    </>
  );
}
