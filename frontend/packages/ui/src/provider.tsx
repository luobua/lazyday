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
 *
 * Portal 默认使用 Dark Mode OLED 主题
 */
export function LazydayProvider({
  children,
  dark = true,
  primaryColor = '#3B82F6',
}: LazydayProviderProps) {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: dark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
        token: {
          colorPrimary: primaryColor,
          colorBgBase: dark ? '#0F172A' : undefined,
          colorBgContainer: dark ? '#1E293B' : undefined,
          colorText: dark ? '#F8FAFC' : undefined,
          colorTextSecondary: dark ? '#94A3B8' : undefined,
          colorSuccess: '#22C55E',
          borderRadius: 6,
          fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
        },
      }}
    >
      <App>{children}</App>
    </ConfigProvider>
  );
}
