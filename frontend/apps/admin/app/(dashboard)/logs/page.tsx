'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function AdminLogsPage() {
  return (
    <>
      <PageHeader title="全局调用日志" subtitle="查看所有租户的调用记录" />
      <Card variant="borderless">
        <Empty description="全局日志功能将在 Phase 2 实现后可用" />
      </Card>
    </>
  );
}
