'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function TenantsPage() {
  return (
    <>
      <PageHeader title="租户管理" subtitle="查看和管理所有租户" />
      <Card variant="borderless">
        <Empty description="租户管理功能将在 Phase 3 实现后可用" />
      </Card>
    </>
  );
}
