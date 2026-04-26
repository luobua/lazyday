'use client';

import React, { useState } from 'react';
import { Form, Input, Button, Typography, message, Space } from 'antd';
import { MailOutlined } from '@ant-design/icons';
import Link from 'next/link';

const { Text, Title } = Typography;

export default function ForgotPasswordPage() {
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  const onFinish = async (values: { email: string }) => {
    setLoading(true);
    try {
      // TODO: 替换为实际 API 调用
      console.log('Reset password for:', values.email);
      setSent(true);
      message.success('重置邮件已发送');
    } catch {
      message.error('发送失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  if (sent) {
    return (
      <div style={{ textAlign: 'center' }}>
        <Title level={4}>邮件已发送</Title>
        <Text type="secondary">
          请查收邮箱中的重置密码链接。如果没有收到，请检查垃圾邮件或重新发送。
        </Text>
        <div style={{ marginTop: 16 }}>
          <Link href="/portal/login">返回登录</Link>
        </div>
      </div>
    );
  }

  return (
    <>
      <Title level={4} style={{ textAlign: 'center', marginBottom: 24 }}>找回密码</Title>
      <Form name="forgot-password" layout="vertical" onFinish={onFinish}>
        <Form.Item
          name="email"
          rules={[
            { required: true, message: '请输入注册邮箱' },
            { type: 'email', message: '请输入有效的邮箱地址' },
          ]}
        >
          <Input prefix={<MailOutlined />} placeholder="注册邮箱" size="large" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block size="large">
            发送重置链接
          </Button>
        </Form.Item>
        <Space style={{ width: '100%', justifyContent: 'center' }}>
          <Link href="/portal/login">返回登录</Link>
        </Space>
      </Form>
    </>
  );
}
