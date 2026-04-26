'use client';

import React, { useState } from 'react';
import { Form, Input, Button, Checkbox, Typography, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useRouter } from 'next/navigation';

interface LoginFormValues {
  username: string;
  password: string;
  remember?: boolean;
}

export default function AdminLoginPage() {
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const onFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      // TODO: 替换为 adminAuthApi.login(values)
      console.log('Admin login attempt:', values);
      message.success('登录功能将在 Backend 就绪后启用');
      router.push('/admin/overview');
    } catch (err: unknown) {
      const error = err as Error;
      message.error(error.message || '登录失败');
    } finally {
      setLoading(false);
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
        <Button type="primary" htmlType="submit" loading={loading} block size="large" style={{ background: '#722ed1', borderColor: '#722ed1' }}>
          登录管理后台
        </Button>
      </Form.Item>
    </Form>
  );
}
