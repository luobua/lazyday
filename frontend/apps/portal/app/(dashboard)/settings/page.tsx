'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="设置" subtitle="租户信息和账户配置" />
      <Card variant="borderless">
        <Empty description="设置页面将在 Backend 租户域就绪后完善" />
      </Card>
    </>
  );
}
