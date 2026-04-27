import type { Metadata } from 'next';
import { AntdRegistry } from '@ant-design/nextjs-registry';
import { App as AntApp } from 'antd';
import { LazydayProvider } from '@lazyday/ui';
import { QueryProvider } from '@/lib/query-provider';
import './globals.css';

export const metadata: Metadata = {
  title: 'Lazyday Developer Portal',
  description: 'Lazyday 多租户开放平台 — 开发者控制台',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <AntdRegistry>
          <AntApp>
            <LazydayProvider>
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
