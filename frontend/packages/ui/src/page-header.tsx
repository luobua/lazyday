'use client';

import React from 'react';
import { Breadcrumb, Typography } from 'antd';
import { HomeOutlined } from '@ant-design/icons';

const { Title } = Typography;

export interface PageHeaderProps {
  title: string;
  subtitle?: string;
  breadcrumbItems?: Array<{ label: string; href?: string }>;
  extra?: React.ReactNode;
}

/**
 * 页面头部：面包屑 + 标题 + 操作区
 *
 * 注意：breadcrumb href 仅作为文本展示，不渲染为链接。
 * 路由导航由各应用自行处理。
 */
export function PageHeader({ title, subtitle, breadcrumbItems, extra }: PageHeaderProps) {
  const items = [
    { title: <><HomeOutlined /> 首页</> },
    ...(breadcrumbItems || []).map((item, index) => ({
      title: item.label,
      key: `bc-${index}`,
    })),
    { title, key: 'bc-current' },
  ];

  return (
    <div style={{ marginBottom: 24 }}>
      <Breadcrumb items={items} />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginTop: 12 }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>{title}</Title>
          {subtitle && (
            <Typography.Text type="secondary" style={{ marginTop: 4, display: 'block' }}>
              {subtitle}
            </Typography.Text>
          )}
        </div>
        {extra && <div>{extra}</div>}
      </div>
    </div>
  );
}

export { HomeOutlined } from '@ant-design/icons';
