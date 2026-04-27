'use client';

import React, { useState } from 'react';
import { Form, Input, Button, Typography, Space, App } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined, BankOutlined } from '@ant-design/icons';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useRegister } from '@/hooks/use-auth';

const { Text } = Typography;

interface RegisterFormValues {
  username: string;
  email: string;
  password: string;
  confirm_password: string;
  tenant_name: string;
}

function getPasswordStrength(password: string): { level: number; color: string; label: string } {
  if (!password) return { level: 0, color: '#303030', label: '' };
  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score++;
  if (/\d/.test(password)) score++;
  if (/[^a-zA-Z0-9]/.test(password)) score++;

  if (score <= 2) return { level: 1, color: '#ef4444', label: '弱' };
  if (score <= 3) return { level: 2, color: '#f97316', label: '中' };
  return { level: 3, color: '#22c55e', label: '强' };
}

export default function RegisterPage() {
  const router = useRouter();
  const [form] = Form.useForm<RegisterFormValues>();
  const registerMutation = useRegister();
  const [passwordStrength, setPasswordStrength] = useState({ level: 0, color: '#303030', label: '' });
  const { message } = App.useApp();

  const onFinish = async (values: RegisterFormValues) => {
    try {
      await registerMutation.mutateAsync({
        username: values.username,
        email: values.email,
        password: values.password,
        tenantName: values.tenant_name,
      });
      router.push('/overview');
    } catch (err: unknown) {
      const error = err as Error & { error_code?: string };
      if (error.error_code === 'DUPLICATE_USERNAME') {
        form.setFields([{ name: 'username', errors: ['用户名已被使用'] }]);
      } else if (error.error_code === 'DUPLICATE_EMAIL') {
        form.setFields([{ name: 'email', errors: ['邮箱已被使用'] }]);
      } else {
        message.error(error.message || '注册失败');
      }
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
        <Input.Password
          prefix={<LockOutlined />}
          placeholder="密码"
          size="large"
          onChange={(e) => setPasswordStrength(getPasswordStrength(e.target.value))}
        />
      </Form.Item>

      {passwordStrength.level > 0 && (
        <div style={{ marginTop: -16, marginBottom: 16 }}>
          <div style={{ display: 'flex', gap: 4, marginBottom: 4 }}>
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                style={{
                  flex: 1,
                  height: 4,
                  borderRadius: 2,
                  backgroundColor: i <= passwordStrength.level ? passwordStrength.color : '#334155',
                  transition: 'background-color 0.2s',
                }}
              />
            ))}
          </div>
          <Text style={{ fontSize: 12, color: passwordStrength.color }}>
            密码强度：{passwordStrength.label}
          </Text>
        </div>
      )}

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
              return Promise.reject(new Error('两次密码不一致'));
            },
          }),
        ]}
      >
        <Input.Password prefix={<LockOutlined />} placeholder="确认密码" size="large" />
      </Form.Item>

      <Form.Item>
        <Button type="primary" htmlType="submit" loading={registerMutation.isPending} block size="large">
          注册
        </Button>
      </Form.Item>

      <Space style={{ width: '100%', justifyContent: 'center' }}>
        <Text type="secondary">已有账号？</Text>
        <Link href="/login">返回登录</Link>
      </Space>
    </Form>
  );
}
