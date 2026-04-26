'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function RagListPage() {
  return (
    <>
      <PageHeader title="RAG 知识库" subtitle="管理向量检索知识库" />
      <Card variant="borderless">
        <Empty description="RAG 知识库功能将在 Phase 4 实现后可用" />
      </Card>
    </>
  );
}
