'use client';

import React, { useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  ApiOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { PageHeader } from '@lazyday/ui';
import { copyToClipboard, formatTimestamp } from '@lazyday/utils';
import type { WebhookConfig, WebhookCreateRequest, WebhookEventType, WebhookTestResult } from '@lazyday/types';
import {
  useCreateWebhook,
  useDeleteWebhook,
  useRotateWebhookSecret,
  useTestWebhook,
  useUpdateWebhook,
  useWebhooks,
} from '@/hooks/use-webhooks';

const { Paragraph, Text } = Typography;

const EVENT_OPTIONS: Array<{ label: string; value: WebhookEventType }> = [
  { label: 'AppKey 禁用', value: 'appkey.disabled' },
  { label: 'AppKey 轮换', value: 'appkey.rotated' },
  { label: '租户暂停', value: 'tenant.suspended' },
  { label: '租户恢复', value: 'tenant.resumed' },
  { label: '配额耗尽', value: 'quota.exceeded' },
  { label: '套餐变更', value: 'quota.plan_changed' },
  { label: 'Webhook 永久失败', value: 'webhook.permanent_failed' },
];

type WebhookFormValues = WebhookCreateRequest;

export default function WebhooksPage() {
  const { data: webhooks, isLoading } = useWebhooks();
  const createMutation = useCreateWebhook();
  const updateMutation = useUpdateWebhook();
  const deleteMutation = useDeleteWebhook();
  const rotateMutation = useRotateWebhookSecret();
  const testMutation = useTestWebhook();
  const { message } = App.useApp();

  const [form] = Form.useForm<WebhookFormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<WebhookConfig | null>(null);
  const [secretModal, setSecretModal] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<WebhookTestResult | null>(null);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: WebhookConfig) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.name,
      url: record.url,
      event_types: record.event_types,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editing) {
        await updateMutation.mutateAsync({ id: editing.id, data: values });
        message.success('Webhook 已更新');
      } else {
        const created = await createMutation.mutateAsync(values);
        message.success('Webhook 已创建');
        if (created.secret) {
          setSecretModal(created.secret);
        }
      }
      setModalOpen(false);
      form.resetFields();
    } catch (error) {
      if (error instanceof Error) {
        message.error(error.message);
      }
    }
  };

  const handleToggle = async (record: WebhookConfig) => {
    const nextStatus = record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await updateMutation.mutateAsync({ id: record.id, data: { status: nextStatus } });
      message.success(nextStatus === 'ACTIVE' ? 'Webhook 已启用' : 'Webhook 已停用');
    } catch (error) {
      message.error((error as Error).message || '操作失败');
    }
  };

  const handleRotate = async (id: number) => {
    try {
      const result = await rotateMutation.mutateAsync(id);
      if (result.secret) {
        setSecretModal(result.secret);
      }
      message.success('Secret 已轮换');
    } catch (error) {
      message.error((error as Error).message || '轮换失败');
    }
  };

  const handleTest = async (id: number) => {
    try {
      const result = await testMutation.mutateAsync(id);
      setTestResult(result);
    } catch (error) {
      message.error((error as Error).message || '测试推送失败');
    }
  };

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: WebhookConfig) => (
        <Space direction="vertical" size={2}>
          <Space>
            <Text strong>{name}</Text>
            <Tag color={record.status === 'ACTIVE' ? 'success' : 'default'}>
              {record.status === 'ACTIVE' ? '启用' : '停用'}
            </Tag>
          </Space>
          <Text type="secondary" copyable style={{ fontSize: 12 }}>{record.url}</Text>
        </Space>
      ),
    },
    {
      title: '事件类型',
      dataIndex: 'event_types',
      key: 'event_types',
      render: (events: WebhookEventType[]) => (
        <Space wrap>
          {events.map((event) => (
            <Tag key={event} color="blue">{event}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'update_time',
      key: 'update_time',
      width: 180,
      render: (value?: string, record?: WebhookConfig) => formatTimestamp(value || record?.create_time || ''),
    },
    {
      title: '操作',
      key: 'actions',
      width: 360,
      render: (_: unknown, record: WebhookConfig) => (
        <Space>
          <Tooltip title="测试推送">
            <Button type="link" size="small" icon={<ApiOutlined />} onClick={() => handleTest(record.id)}>
              测试
            </Button>
          </Tooltip>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确定轮换 Secret？" onConfirm={() => handleRotate(record.id)}>
            <Button type="link" size="small" icon={<ReloadOutlined />}>轮换</Button>
          </Popconfirm>
          <Button
            type="link"
            size="small"
            icon={record.status === 'ACTIVE' ? <StopOutlined /> : <PlayCircleOutlined />}
            onClick={() => handleToggle(record)}
          >
            {record.status === 'ACTIVE' ? '停用' : '启用'}
          </Button>
          <Popconfirm
            title="确定删除此 Webhook？"
            description="删除后不会再接收事件推送"
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
        title="Webhook 管理"
        subtitle="配置事件回调通知"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            创建 Webhook
          </Button>
        }
      />

      <Card variant="borderless">
        {isLoading ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : !webhooks || webhooks.length === 0 ? (
          <Empty description="还没有 Webhook">
            <Button type="primary" onClick={openCreate}>创建第一个 Webhook</Button>
          </Empty>
        ) : (
          <Table
            dataSource={webhooks}
            columns={columns}
            rowKey="id"
            pagination={false}
            scroll={{ x: 980 }}
          />
        )}
      </Card>

      <Modal
        title={editing ? '编辑 Webhook' : '创建 Webhook'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入 Webhook 名称' }]}>
            <Input placeholder="例如：生产事件回调" />
          </Form.Item>
          <Form.Item
            name="url"
            label="URL"
            rules={[
              { required: true, message: '请输入回调 URL' },
              {
                validator: (_, value?: string) => {
                  if (!value || value.startsWith('https://')) return Promise.resolve();
                  return Promise.reject(new Error('URL 必须以 https:// 开头'));
                },
              },
            ]}
          >
            <Input placeholder="https://example.com/lazyday/webhook" />
          </Form.Item>
          <Form.Item
            name="event_types"
            label="事件类型"
            rules={[{ required: true, type: 'array', min: 1, message: '至少选择一个事件类型' }]}
          >
            <Select mode="multiple" options={EVENT_OPTIONS} placeholder="选择事件类型" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="请保存 Webhook Secret"
        open={!!secretModal}
        onCancel={() => setSecretModal(null)}
        footer={<Button type="primary" onClick={() => setSecretModal(null)}>我已保存，关闭</Button>}
        maskClosable={false}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Alert type="warning" message="Secret 仅显示一次，关闭后无法再次查看。" showIcon />
          <Paragraph code copyable style={{ fontFamily: "'Fira Code', monospace", fontSize: 13 }}>
            {secretModal}
          </Paragraph>
          <Button
            icon={<CopyOutlined />}
            onClick={() => {
              if (secretModal) copyToClipboard(secretModal);
              message.success('已复制');
            }}
          >
            复制 Secret
          </Button>
        </Space>
      </Modal>

      <Modal
        title="测试推送结果"
        open={!!testResult}
        onCancel={() => setTestResult(null)}
        footer={<Button type="primary" onClick={() => setTestResult(null)}>关闭</Button>}
        width={760}
      >
        {testResult && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="HTTP 状态">
                {testResult.http_status ? <Tag color={testResult.http_status < 300 ? 'success' : 'error'}>{testResult.http_status}</Tag> : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="耗时">{testResult.latency_ms} ms</Descriptions.Item>
              <Descriptions.Item label="错误码">{testResult.error_code || '-'}</Descriptions.Item>
              <Descriptions.Item label="错误">{testResult.error || '-'}</Descriptions.Item>
            </Descriptions>
            <div>
              <Text type="secondary">响应头</Text>
              <Paragraph code style={{ whiteSpace: 'pre-wrap' }}>
                {JSON.stringify(testResult.response_headers || {}, null, 2)}
              </Paragraph>
            </div>
            <div>
              <Text type="secondary">响应体</Text>
              <Paragraph code style={{ whiteSpace: 'pre-wrap' }}>
                {testResult.response_body_excerpt || '-'}
              </Paragraph>
            </div>
          </Space>
        )}
      </Modal>
    </>
  );
}
