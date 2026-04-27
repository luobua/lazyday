'use client';

import React, { useState } from 'react';
import { Layout, Menu, Avatar, Dropdown, Typography, theme } from 'antd';
import type { MenuProps } from 'antd';
import {
  DashboardOutlined,
  KeyOutlined,
  FileTextOutlined,
  SendOutlined,
  DatabaseOutlined,
  RobotOutlined,
  ApartmentOutlined,
  SettingOutlined,
  UserOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BookOutlined,
} from '@ant-design/icons';
import { usePathname, useRouter } from 'next/navigation';
import { useLogout } from '@/hooks/use-auth';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

const menuItems: MenuProps['items'] = [
  {
    key: '/overview',
    icon: <DashboardOutlined />,
    label: '概览',
  },
  {
    key: '/credentials',
    icon: <KeyOutlined />,
    label: 'AppKey 管理',
  },
  {
    key: '/logs',
    icon: <FileTextOutlined />,
    label: '调用日志',
  },
  {
    key: '/webhooks',
    icon: <SendOutlined />,
    label: 'Webhook',
  },
  {
    key: '/docs',
    icon: <BookOutlined />,
    label: 'API 文档',
  },
  { type: 'divider' },
  {
    key: '/rag',
    icon: <DatabaseOutlined />,
    label: 'RAG 知识库',
  },
  {
    key: '/agent',
    icon: <RobotOutlined />,
    label: 'Agent 配置',
  },
  {
    key: '/workflow',
    icon: <ApartmentOutlined />,
    label: 'Workflow 编排',
  },
  { type: 'divider' },
  {
    key: '/settings',
    icon: <SettingOutlined />,
    label: '设置',
  },
];

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const [collapsed, setCollapsed] = useState(false);
  const pathname = usePathname();
  const router = useRouter();
  const { token } = theme.useToken();
  const logoutMutation = useLogout();

  const selectedKeys = [pathname];
  const openKeys: string[] = [];

  const onMenuClick: MenuProps['onClick'] = ({ key }) => {
    router.push(key);
  };

  const onLogout = () => {
    logoutMutation.mutate(undefined, {
      onSuccess: () => router.push('/login'),
    });
  };

  const userMenuItems: MenuProps['items'] = [
    { key: 'profile', icon: <UserOutlined />, label: '个人资料' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ];

  const onUserMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'logout') {
      onLogout();
    }
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        theme="light"
        style={{
          borderRight: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: collapsed ? 'center' : 'flex-start',
            padding: collapsed ? 0 : '0 20px',
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <span
            style={{
              fontSize: collapsed ? 20 : 18,
              fontWeight: 700,
              color: token.colorPrimary,
              whiteSpace: 'nowrap',
            }}
          >
            {collapsed ? 'L' : 'Lazyday Portal'}
          </span>
        </div>
        <Menu
          mode="inline"
          selectedKeys={selectedKeys}
          defaultOpenKeys={openKeys}
          items={menuItems}
          onClick={onMenuClick}
          style={{ border: 'none', marginTop: 8 }}
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
          <div
            style={{ cursor: 'pointer', fontSize: 18 }}
            onClick={() => setCollapsed(!collapsed)}
          >
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </div>
          <Dropdown menu={{ items: userMenuItems, onClick: onUserMenuClick }} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar size="small" icon={<UserOutlined />} />
              <Text>开发者</Text>
            </div>
          </Dropdown>
        </Header>
        <Content className="dashboard-content" key={pathname}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
}
