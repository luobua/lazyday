import type { Metadata } from 'next';
import { AntdRegistry } from '@ant-design/nextjs-registry';
import { App as AntApp } from 'antd';
import { LazydayProvider } from '@lazyday/ui';
import { QueryProvider } from '@/lib/query-provider';
import './globals.css';

export const metadata: Metadata = {
  title: 'Lazyday Admin Console',
  description: 'Lazyday 多租户开放平台 — 运营管理后台',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <AntdRegistry>
          <AntApp>
            <LazydayProvider primaryColor="#722ed1">
              <QueryProvider>
                {children}
              </QueryProvider>
            </LazydayProvider>
          </AntApp>
        </AntdRegistry>
      </body>
    </html>
  );
}
