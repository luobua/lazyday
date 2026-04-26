'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function TenantDetailPage() {
  return (
    <>
      <PageHeader title="租户详情" subtitle="查看租户信息和操作" />
      <Card variant="borderless">
        <Empty description="租户详情功能将在 Phase 3 实现后可用" />
      </Card>
    </>
  );
}
