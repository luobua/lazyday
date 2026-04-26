'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function PlansPage() {
  return (
    <>
      <PageHeader title="套餐管理" subtitle="管理平台套餐和配额" />
      <Card variant="borderless">
        <Empty description="套餐管理功能将在 Phase 2-3 实现后可用" />
      </Card>
    </>
  );
}
