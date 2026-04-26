'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function AgentListPage() {
  return (
    <>
      <PageHeader title="Agent 配置" subtitle="管理 AI Agent 角色和工具" />
      <Card variant="borderless">
        <Empty description="Agent 配置功能将在 Phase 4 实现后可用" />
      </Card>
    </>
  );
}
