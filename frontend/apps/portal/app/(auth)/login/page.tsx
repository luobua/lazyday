'use client';

import React from 'react';
import { Form, Input, Button, Checkbox, Typography, Space, App } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { useLogin } from '@/hooks/use-auth';

const { Text } = Typography;

interface LoginFormValues {
  username: string;
  password: string;
  remember?: boolean;
}

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const loginMutation = useLogin();
  const { message } = App.useApp();

  const onFinish = async (values: LoginFormValues) => {
    try {
      await loginMutation.mutateAsync({
        username: values.username,
        password: values.password,
        remember: values.remember,
      });
      const redirect = searchParams.get('redirect') || '/overview';
      router.push(redirect);
    } catch (err: unknown) {
      const error = err as Error & { error_code?: string };
      if (error.error_code === 'INVALID_CREDENTIALS') {
        message.error('用户名或密码错误');
      } else if (error.error_code === 'ACCOUNT_DISABLED') {
        message.error('账户已禁用');
      } else {
        message.error(error.message || '登录失败');
      }
    }
  };

  return (
    <Form
      name="login"
      layout="vertical"
      onFinish={onFinish}
      autoComplete="off"
      initialValues={{ remember: true }}
    >
      <Form.Item
        name="username"
        rules={[{ required: true, message: '请输入用户名' }]}
      >
        <Input prefix={<UserOutlined />} placeholder="用户名" size="large" />
      </Form.Item>

      <Form.Item
        name="password"
        rules={[{ required: true, message: '请输入密码' }]}
      >
        <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
      </Form.Item>

      <Form.Item>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Form.Item name="remember" valuePropName="checked" noStyle>
            <Checkbox>记住我</Checkbox>
          </Form.Item>
          <Link href="/forgot-password">
            <Text type="secondary">忘记密码？</Text>
          </Link>
        </div>
      </Form.Item>

      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loginMutation.isPending} block size="large">
          登录
        </Button>
      </Form.Item>

      <Space style={{ width: '100%', justifyContent: 'center' }}>
        <Text type="secondary">还没有账号？</Text>
        <Link href="/register">立即注册</Link>
      </Space>
    </Form>
  );
}
