'use client';

import React, { useState } from 'react';
import { Card, Table, Button, Tag, Space, Modal, Form, Input, Typography, message, Tooltip, Popconfirm } from 'antd';
import { PlusOutlined, CopyOutlined, ReloadOutlined, StopOutlined, PlayCircleOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons';
import { PageHeader } from '@lazyday/ui';
import type { AppKeyInfo } from '@lazyday/types';
import { formatTimestamp, copyToClipboard, maskAppKey } from '@lazyday/utils';

const { Text, Paragraph } = Typography;

// Mock 数据
const mockAppKeys: AppKeyInfo[] = [
  {
    id: 1,
    name: '生产环境',
    app_key: 'ak_prod_8f3a2b1c4d5e6f7a8b9c0d1e2f3a4b5c',
    secret_key_masked: 'sk_****4b5c',
    status: 'active',
    scopes: ['ai:chat', 'ai:tts', 'ai:asr'],
    created_at: '2026-01-15T08:00:00Z',
    updated_at: '2026-04-20T14:30:00Z',
  },
  {
    id: 2,
    name: '测试环境',
    app_key: 'ak_test_1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d',
    secret_key_masked: 'sk_****5c6d',
    status: 'active',
    scopes: ['ai:chat'],
    created_at: '2026-03-01T10:00:00Z',
    updated_at: '2026-04-25T09:15:00Z',
  },
  {
    id: 3,
    name: '旧版 SDK',
    app_key: 'ak_old_9z8y7x6w5v4u3t2s1r0q9p8o7n6m5l4k',
    secret_key_masked: 'sk_****5l4k',
    status: 'disabled',
    scopes: ['ai:chat'],
    created_at: '2025-10-01T12:00:00Z',
    updated_at: '2026-04-01T16:00:00Z',
  },
];

export default function CredentialsPage() {
  const [appKeys] = useState(mockAppKeys);
  const [createOpen, setCreateOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedKey, setSelectedKey] = useState<AppKeyInfo | null>(null);
  const [form] = Form.useForm();

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: AppKeyInfo) => (
        <Space>
          <Text strong>{name}</Text>
          {record.status === 'disabled' && <Tag color="error">已禁用</Tag>}
        </Space>
      ),
    },
    {
      title: 'AppKey',
      dataIndex: 'app_key',
      key: 'app_key',
      render: (key: string) => (
        <Space>
          <Text code style={{ fontSize: 12 }}>
            {maskAppKey(key)}
          </Text>
          <Tooltip title="复制 AppKey">
            <Button
              type="text"
              size="small"
              icon={<CopyOutlined />}
              onClick={() => {
                copyToClipboard(key);
                message.success('已复制');
              }}
            />
          </Tooltip>
        </Space>
      ),
    },
    {
      title: '权限范围',
      dataIndex: 'scopes',
      key: 'scopes',
      render: (scopes: string[]) => (
        <Space wrap>
          {scopes.map((s) => (
            <Tag key={s} color="blue">{s}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 180,
      render: (t: string) => formatTimestamp(t),
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_: unknown, record: AppKeyInfo) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => { setSelectedKey(record); setDetailOpen(true); }}
          >
            详情
          </Button>
          {record.status === 'active' ? (
            <Popconfirm title="确定禁用此 AppKey？" onConfirm={() => message.info('禁用功能待 Backend 就绪')}>
              <Button type="link" size="small" icon={<StopOutlined />} danger>禁用</Button>
            </Popconfirm>
          ) : (
            <Button type="link" size="small" icon={<PlayCircleOutlined />} style={{ color: '#52c41a' }}>
              启用
            </Button>
          )}
          <Button type="link" size="small" icon={<ReloadOutlined />}>
            轮换密钥
          </Button>
          <Popconfirm title="确定删除此 AppKey？删除后不可恢复" onConfirm={() => message.info('删除功能待 Backend 就绪')}>
            <Button type="link" size="small" icon={<DeleteOutlined />} danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="AppKey 管理"
        subtitle="管理 API 调用凭证"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            创建 AppKey
          </Button>
        }
      />

      <Card variant="borderless">
        <Table
          dataSource={appKeys}
          columns={columns}
          rowKey="id"
          pagination={false}
        />
      </Card>

      {/* 创建 AppKey 弹窗 */}
      <Modal
        title="创建 AppKey"
        open={createOpen}
        onCancel={() => { setCreateOpen(false); form.resetFields(); }}
        onOk={() => {
          form.validateFields().then(() => {
            message.success('创建功能将在 Backend 就绪后启用');
            setCreateOpen(false);
            form.resetFields();
          });
        }}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入 AppKey 名称' }]}>
            <Input placeholder="例如：生产环境、测试环境" />
          </Form.Item>
          <Form.Item name="scopes" label="权限范围">
            <Input placeholder="例如：ai:chat, ai:tts（留空则默认全部权限）" />
          </Form.Item>
        </Form>
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          创建成功后，SecretKey 仅显示一次，请妥善保存。
        </Paragraph>
      </Modal>

      {/* 详情弹窗 */}
      <Modal
        title="AppKey 详情"
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
      >
        {selectedKey && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <div><Text type="secondary">名称：</Text><Text strong>{selectedKey.name}</Text></div>
            <div><Text type="secondary">状态：</Text><Tag color={selectedKey.status === 'active' ? 'success' : 'error'}>{selectedKey.status === 'active' ? '活跃' : '已禁用'}</Tag></div>
            <div>
              <Text type="secondary">AppKey：</Text>
              <Text code copyable>{selectedKey.app_key}</Text>
            </div>
            <div>
              <Text type="secondary">SecretKey：</Text>
              <Text code>{selectedKey.secret_key_masked}</Text>
            </div>
            <div><Text type="secondary">权限：</Text><Space wrap>{selectedKey.scopes.map(s => <Tag key={s}>{s}</Tag>)}</Space></div>
            <div><Text type="secondary">创建时间：</Text><Text>{formatTimestamp(selectedKey.created_at)}</Text></div>
          </Space>
        )}
      </Modal>
    </>
  );
}
