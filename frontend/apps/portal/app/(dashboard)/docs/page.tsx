'use client';

import React from 'react';
import { Card, Empty } from 'antd';
import { PageHeader } from '@lazyday/ui';

export default function DocsPage() {
  return (
    <>
      <PageHeader title="API 文档" subtitle="查看平台开放接口文档" />
      <Card variant="outlined">
        <Empty
          description={
            <span>
              API 文档将在 Phase 3 实现。
              <br />
              Swagger UI 将内嵌于此页面，渲染{' '}
              <code>/api/open/v1</code> 的 OpenAPI 规范。
            </span>
          }
        />
      </Card>
    </>
  );
}