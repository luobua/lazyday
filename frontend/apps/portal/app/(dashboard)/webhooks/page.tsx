'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function WebhooksPage() {
  return (
    <>
      <PageHeader title="Webhook 管理" subtitle="配置事件回调通知" />
      <Card variant="borderless">
        <Empty description="Webhook 功能将在 Phase 3 实现后可用" />
      </Card>
    </>
  );
}
