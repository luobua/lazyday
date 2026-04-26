'use client';

import React from 'react';
import { Card, Empty, Typography } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function LogsPage() {
  return (
    <>
      <PageHeader title="调用日志" subtitle="查看 API 调用记录和统计分析" />
      <Card variant="borderless">
        <Empty description="调用日志功能将在 Phase 2 实现后可用" />
      </Card>
    </>
  );
}
