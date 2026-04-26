'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function AdminSettingsPage() {
  return (
    <>
      <PageHeader title="系统设置" subtitle="平台全局配置" />
      <Card variant="borderless">
        <Empty description="系统设置功能将在后续迭代中完善" />
      </Card>
    </>
  );
}
