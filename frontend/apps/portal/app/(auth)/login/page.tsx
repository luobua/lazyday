'use client';

import React, { useState } from 'react';
import { Form, Input, Button, Checkbox, Typography, message, Space } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

const { Text } = Typography;

interface LoginFormValues {
  username: string;
  password: string;
  remember?: boolean;
}

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const onFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      // TODO: 替换为实际 API 调用（Backend 就绪后）
      // const res = await authApi.login(values);
      // if (res.code === 0) {
      //   router.push('/portal/overview');
      // }

      // 模拟登录成功
      console.log('Login attempt:', values);
      message.success('登录功能将在 Backend 就绪后启用');
      router.push('/portal/overview');
    } catch (err: unknown) {
      const error = err as Error;
      message.error(error.message || '登录失败');
    } finally {
      setLoading(false);
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
          <Link href="/portal/forgot-password">
            <Text type="secondary">忘记密码？</Text>
          </Link>
        </div>
      </Form.Item>

      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block size="large">
          登录
        </Button>
      </Form.Item>

      <Space style={{ width: '100%', justifyContent: 'center' }}>
        <Text type="secondary">还没有账号？</Text>
        <Link href="/portal/register">立即注册</Link>
      </Space>
    </Form>
  );
}
