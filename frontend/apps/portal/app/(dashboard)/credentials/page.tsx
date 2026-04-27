'use client';

import React, { useState } from 'react';
import { Card, Table, Button, Tag, Space, Modal, Form, Input, Select, Typography, Tooltip, Popconfirm, Empty, Alert, Skeleton, App } from 'antd';
import { PlusOutlined, CopyOutlined, ReloadOutlined, StopOutlined, PlayCircleOutlined, DeleteOutlined, EyeOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { PageHeader } from '@lazyday/ui';
import { copyToClipboard } from '@lazyday/utils';
import {
  useCredentials,
  useCreateCredential,
  useDisableCredential,
  useEnableCredential,
  useRotateSecret,
  useDeleteCredential,
} from '@/hooks/use-credentials';

const { Text, Paragraph } = Typography;

const SCOPE_OPTIONS = [
  { label: 'AI 对话', value: 'ai:chat' },
  { label: 'AI 语音合成', value: 'ai:tts' },
  { label: 'AI 语音识别', value: 'ai:asr' },
];

interface SecretKeyModalData {
  appKey: string;
  secretKey: string;
}

export default function CredentialsPage() {
  const { data: appKeys, isLoading } = useCredentials();
  const createMutation = useCreateCredential();
  const disableMutation = useDisableCredential();
  const enableMutation = useEnableCredential();
  const rotateMutation = useRotateSecret();
  const deleteMutation = useDeleteCredential();

  const [createOpen, setCreateOpen] = useState(false);
  const [secretModal, setSecretModal] = useState<SecretKeyModalData | null>(null);
  const [form] = Form.useForm();
  const { message } = App.useApp();

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      const result = await createMutation.mutateAsync({
        name: values.name,
        scopes: values.scopes,
      });
      setCreateOpen(false);
      form.resetFields();
      setSecretModal({
        appKey: result.app_key,
        secretKey: result.secret_key,
      });
    } catch {
      // validation error
    }
  };

  const handleRotate = async (id: number) => {
    try {
      const result = await rotateMutation.mutateAsync(id);
      setSecretModal({
        appKey: '',
        secretKey: result.secret_key,
      });
    } catch (err: unknown) {
      message.error((err as Error).message || '轮换失败');
    }
  };

  const handleCloseSecretModal = () => {
    Modal.confirm({
      title: '确定已保存 SecretKey？',
      content: '关闭后无法再次查看',
      icon: <ExclamationCircleOutlined />,
      okText: '已保存，关闭',
      cancelText: '返回',
      onOk: () => setSecretModal(null),
    });
  };

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: { status: string }) => (
        <Space>
          <Text strong>{name}</Text>
          <Tag color={record.status === 'ACTIVE' ? 'success' : 'error'}>
            {record.status === 'ACTIVE' ? '活跃' : '已禁用'}
          </Tag>
        </Space>
      ),
    },
    {
      title: 'AppKey',
      dataIndex: 'app_key',
      key: 'app_key',
      render: (key: string) => (
        <Space>
          <Text code style={{ fontSize: 12 }}>{key}</Text>
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
      render: (scopes: string) => (
        <Space wrap>
          {scopes?.split(',').filter(Boolean).map((s: string) => (
            <Tag key={s} color="blue">{s.trim()}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'create_time',
      key: 'create_time',
      width: 180,
      render: (t: string) => t ? new Date(t).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 300,
      render: (_: unknown, record: { id: number; status: string }) => (
        <Space>
          {record.status === 'ACTIVE' ? (
            <Popconfirm
              title="确定禁用此 AppKey？"
              description="禁用后使用该 Key 的请求将被拒绝"
              onConfirm={() => disableMutation.mutate(record.id)}
            >
              <Button type="link" size="small" icon={<StopOutlined />} danger>禁用</Button>
            </Popconfirm>
          ) : (
            <Popconfirm
              title="确定启用此 AppKey？"
              onConfirm={() => enableMutation.mutate(record.id)}
            >
              <Button type="link" size="small" icon={<PlayCircleOutlined />} style={{ color: '#52c41a' }}>
                启用
              </Button>
            </Popconfirm>
          )}
          <Popconfirm
            title="确定轮换密钥？"
            description="旧密钥将在 24 小时后失效"
            onConfirm={() => handleRotate(record.id)}
          >
            <Button type="link" size="small" icon={<ReloadOutlined />}>轮换密钥</Button>
          </Popconfirm>
          <Popconfirm
            title="确定删除此 AppKey？"
            description="删除后不可恢复"
            onConfirm={() => deleteMutation.mutate(record.id)}
          >
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
        {isLoading ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : !appKeys || appKeys.length === 0 ? (
          <Empty description="还没有 AppKey">
            <Button type="primary" onClick={() => setCreateOpen(true)}>
              创建第一个 AppKey
            </Button>
          </Empty>
        ) : (
          <Table
            dataSource={appKeys}
            columns={columns}
            rowKey="id"
            pagination={false}
          />
        )}
      </Card>

      {/* 创建 AppKey 弹窗 */}
      <Modal
        title="创建 AppKey"
        open={createOpen}
        onCancel={() => { setCreateOpen(false); form.resetFields(); }}
        onOk={handleCreate}
        confirmLoading={createMutation.isPending}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入 AppKey 名称' }]}>
            <Input placeholder="例如：生产环境、测试环境" />
          </Form.Item>
          <Form.Item name="scopes" label="权限范围">
            <Select
              mode="multiple"
              placeholder="选择权限范围（留空则默认全部权限）"
              options={SCOPE_OPTIONS}
            />
          </Form.Item>
        </Form>
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          创建成功后，SecretKey 仅显示一次，请妥善保存。
        </Paragraph>
      </Modal>

      {/* 一次性 SecretKey 展示 */}
      <Modal
        title="请保存您的密钥"
        open={!!secretModal}
        onCancel={handleCloseSecretModal}
        maskClosable={false}
        footer={[
          <Button key="close" type="primary" onClick={handleCloseSecretModal}>
            我已保存，关闭
          </Button>,
        ]}
      >
        {secretModal && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Alert
              type="warning"
              message="SecretKey 仅显示一次，关闭后无法再次查看，请立即复制并妥善保存。"
              showIcon
            />
            {secretModal.appKey && (
              <div>
                <Text type="secondary">AppKey：</Text>
                <Paragraph
                  code
                  copyable
                  style={{ fontFamily: "'Fira Code', monospace", fontSize: 13 }}
                >
                  {secretModal.appKey}
                </Paragraph>
              </div>
            )}
            <div>
              <Text type="secondary">SecretKey：</Text>
              <Paragraph
                code
                copyable
                style={{ fontFamily: "'Fira Code', monospace", fontSize: 13 }}
              >
                {secretModal.secretKey}
              </Paragraph>
            </div>
          </Space>
        )}
      </Modal>
    </>
  );
}
