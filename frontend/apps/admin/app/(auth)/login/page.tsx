'use client';

import React from 'react';
import { Form, Input, Button, Checkbox, App } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useRouter } from 'next/navigation';
import { useAdminLogin } from '@/hooks/use-auth';

interface LoginFormValues {
  username: string;
  password: string;
  remember?: boolean;
}

export default function AdminLoginPage() {
  const { message } = App.useApp();
  const router = useRouter();
  const loginMutation = useAdminLogin();

  const onFinish = async (values: LoginFormValues) => {
    try {
      await loginMutation.mutateAsync({
        username: values.username,
        password: values.password,
        remember: values.remember,
      });
      router.push('/overview');
    } catch (err: unknown) {
      const error = err as Error & { error_code?: string };
      if (error.error_code === 'INVALID_CREDENTIALS') {
        message.error('用户名或密码错误');
      } else if (error.error_code === 'FORBIDDEN_ROLE') {
        message.error('权限不足，非管理员账号');
      } else {
        message.error(error.message || '登录失败');
      }
    }
  };

  return (
    <Form name="admin-login" layout="vertical" onFinish={onFinish} autoComplete="off" initialValues={{ remember: true }}>
      <Form.Item name="username" rules={[{ required: true, message: '请输入管理员账号' }]}>
        <Input prefix={<UserOutlined />} placeholder="管理员账号" size="large" />
      </Form.Item>
      <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
        <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
      </Form.Item>
      <Form.Item>
        <Form.Item name="remember" valuePropName="checked" noStyle>
          <Checkbox>记住我</Checkbox>
        </Form.Item>
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loginMutation.isPending} block size="large" style={{ background: '#722ed1', borderColor: '#722ed1' }}>
          登录管理后台
        </Button>
      </Form.Item>
    </Form>
  );
}
