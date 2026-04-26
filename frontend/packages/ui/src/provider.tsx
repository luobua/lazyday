'use client';

import React from 'react';
import { ConfigProvider, theme as antTheme, App } from 'antd';
import zhCN from 'antd/locale/zh_CN';

export interface LazydayProviderProps {
  children: React.ReactNode;
  /** 是否为暗色主题 */
  dark?: boolean;
  /** 主色 */
  primaryColor?: string;
}

/**
 * 全局 Provider：Ant Design 主题 + 国际化
 */
export function LazydayProvider({
  children,
  dark = false,
  primaryColor = '#1677ff',
}: LazydayProviderProps) {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: dark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
        token: {
          colorPrimary: primaryColor,
          borderRadius: 6,
        },
      }}
    >
      <App>{children}</App>
    </ConfigProvider>
  );
}
