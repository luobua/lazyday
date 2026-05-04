'use client';

import React, { useState } from 'react';
import { Layout, Menu, Avatar, Dropdown, Typography, theme } from 'antd';
import type { MenuProps } from 'antd';
import {
  DashboardOutlined,
  TeamOutlined,
  CrownOutlined,
  FileTextOutlined,
  DatabaseOutlined,
  RobotOutlined,
  ApartmentOutlined,
  SettingOutlined,
  CloudOutlined,
  ThunderboltOutlined,
  UserOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { usePathname, useRouter } from 'next/navigation';
import { useAdminLogout } from '@/hooks/use-auth';
import { EdgeStatusBadge } from './_components/EdgeStatusBadge';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

// 运营管理
const overviewItems: MenuProps['items'] = [
  {
    key: '/overview',
    icon: <DashboardOutlined />,
    label: '系统概览',
  },
  {
    key: '/tenants',
    icon: <TeamOutlined />,
    label: '租户管理',
  },
  {
    key: '/plans',
    icon: <CrownOutlined />,
    label: '套餐管理',
  },
  {
    key: '/logs',
    icon: <FileTextOutlined />,
    label: '调用日志',
  },
];

// AI 平台管理
const aiItems: MenuProps['items'] = [
  {
    key: '/rag',
    icon: <DatabaseOutlined />,
    label: 'RAG 管理',
  },
  {
    key: '/agent',
    icon: <RobotOutlined />,
    label: 'Agent 管理',
  },
  {
    key: '/workflow',
    icon: <ApartmentOutlined />,
    label: 'Workflow 管理',
  },
  {
    key: '/brain-configs',
    icon: <CloudOutlined />,
    label: '配置下发',
  },
  {
    key: '/dispatch-logs',
    icon: <ThunderboltOutlined />,
    label: 'Dispatch Logs',
  },
];

// 系统
const systemItems: MenuProps['items'] = [
  {
    key: '/settings',
    icon: <SettingOutlined />,
    label: '系统设置',
  },
];

const menuItems: MenuProps['items'] = [
  // 运营管理
  { type: 'group', label: '运营管理', children: overviewItems },
  // AI 平台
  { type: 'group', label: 'AI 平台', children: aiItems },
  // 系统
  { type: 'group', label: '系统', children: systemItems },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const [collapsed, setCollapsed] = useState(false);
  const pathname = usePathname();
  const router = useRouter();
  const { token } = theme.useToken();
  const logoutMutation = useAdminLogout();

  const onMenuClick: MenuProps['onClick'] = ({ key }) => {
    router.push(key);
  };

  const onLogout = () => {
    logoutMutation.mutate(undefined, {
      onSuccess: () => router.push('/login'),
    });
  };

  const userMenuItems: MenuProps['items'] = [
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        theme="dark"
        style={{ background: 'linear-gradient(180deg, #1a1a2e 0%, #16213e 100%)' }}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: collapsed ? 'center' : 'flex-start',
            padding: collapsed ? 0 : '0 20px',
            borderBottom: '1px solid rgba(255,255,255,0.1)',
          }}
        >
          <span style={{ fontSize: collapsed ? 20 : 18, fontWeight: 700, color: '#b37feb', whiteSpace: 'nowrap' }}>
            {collapsed ? 'A' : 'Lazyday Admin'}
          </span>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[pathname]}
          items={menuItems}
          onClick={onMenuClick}
          style={{ border: 'none', marginTop: 8, background: 'transparent' }}
          theme="dark"
        />
      </Sider>
      <Layout>
        <Header
          style={{
            padding: '0 24px',
            background: token.colorBgContainer,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ cursor: 'pointer', fontSize: 18 }} onClick={() => setCollapsed(!collapsed)}>
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <EdgeStatusBadge />
            <Dropdown menu={{ items: userMenuItems, onClick: ({ key }) => { if (key === 'logout') onLogout(); } }} placement="bottomRight">
              <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar size="small" style={{ background: '#722ed1' }} icon={<UserOutlined />} />
                <Text>管理员</Text>
              </div>
            </Dropdown>
          </div>
        </Header>
        <Content className="dashboard-content" key={pathname}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
}
