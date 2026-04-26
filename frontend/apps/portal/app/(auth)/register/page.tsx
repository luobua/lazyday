'use client';

import React, { useState } from 'react';
import { Form, Input, Button, Typography, message, Space } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined, BankOutlined } from '@ant-design/icons';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

const { Text } = Typography;

interface RegisterFormValues {
  username: string;
  email: string;
  password: string;
  confirm_password: string;
  tenant_name: string;
}

export default function RegisterPage() {
  const [loading, setLoading] = useState(false);
  const router = useRouter();
  const [form] = Form.useForm<RegisterFormValues>();

  const onFinish = async (values: RegisterFormValues) => {
    setLoading(true);
    try {
      // TODO: 替换为实际 API 调用
      console.log('Register attempt:', values);
      message.success('注册功能将在 Backend 就绪后启用');
      router.push('/portal/login');
    } catch (err: unknown) {
      const error = err as Error;
      message.error(error.message || '注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form
      form={form}
      name="register"
      layout="vertical"
      onFinish={onFinish}
      autoComplete="off"
    >
      <Form.Item
        name="username"
        rules={[
          { required: true, message: '请输入用户名' },
          { min: 3, max: 32, message: '用户名长度 3-32 个字符' },
        ]}
      >
        <Input prefix={<UserOutlined />} placeholder="用户名" size="large" />
      </Form.Item>

      <Form.Item
        name="email"
        rules={[
          { required: true, message: '请输入邮箱' },
          { type: 'email', message: '请输入有效的邮箱地址' },
        ]}
      >
        <Input prefix={<MailOutlined />} placeholder="邮箱" size="large" />
      </Form.Item>

      <Form.Item
        name="tenant_name"
        rules={[
          { required: true, message: '请输入租户名称' },
          { min: 2, max: 64, message: '租户名称长度 2-64 个字符' },
        ]}
      >
        <Input prefix={<BankOutlined />} placeholder="租户名称" size="large" />
      </Form.Item>

      <Form.Item
        name="password"
        rules={[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码至少 8 个字符' },
        ]}
      >
        <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
      </Form.Item>

      <Form.Item
        name="confirm_password"
        dependencies={['password']}
        rules={[
          { required: true, message: '请确认密码' },
          ({ getFieldValue }) => ({
            validator(_, value) {
              if (!value || getFieldValue('password') === value) {
                return Promise.resolve();
              }
              return Promise.reject(new Error('两次输入的密码不一致'));
            },
          }),
        ]}
      >
        <Input.Password prefix={<LockOutlined />} placeholder="确认密码" size="large" />
      </Form.Item>

      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block size="large">
          注册
        </Button>
      </Form.Item>

      <Space style={{ width: '100%', justifyContent: 'center' }}>
        <Text type="secondary">已有账号？</Text>
        <Link href="/portal/login">返回登录</Link>
      </Space>
    </Form>
  );
}
