'use client';

import React, { useState } from 'react';
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { QuotaPlan } from '@lazyday/types';
import { PlusOutlined } from '@ant-design/icons';
import { PageHeader } from '@lazyday/ui';
import { formatNumber, formatTimestamp } from '@lazyday/utils';
import {
  useAdminPlans,
  useCreateAdminPlan,
  useDeleteAdminPlan,
  useUpdateAdminPlan,
} from '@/hooks/use-plans';

interface PlanFormValues {
  name: string;
  qps_limit: number;
  daily_limit: number;
  monthly_limit: number;
  max_app_keys: number;
}

export default function PlansPage() {
  const [form] = Form.useForm<PlanFormValues>();
  const [editingPlan, setEditingPlan] = useState<QuotaPlan | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const { message } = App.useApp();

  const { data: plans, isLoading } = useAdminPlans();
  const createPlanMutation = useCreateAdminPlan();
  const updatePlanMutation = useUpdateAdminPlan();
  const deletePlanMutation = useDeleteAdminPlan();

  const openCreateModal = () => {
    setEditingPlan(null);
    form.resetFields();
    form.setFieldsValue({
      qps_limit: 5,
      daily_limit: 1000,
      monthly_limit: 10000,
      max_app_keys: 5,
    });
    setModalOpen(true);
  };

  const openEditModal = (plan: QuotaPlan) => {
    setEditingPlan(plan);
    form.setFieldsValue({
      name: plan.name,
      qps_limit: plan.qps_limit,
      daily_limit: plan.daily_limit,
      monthly_limit: plan.monthly_limit,
      max_app_keys: plan.max_app_keys,
    });
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditingPlan(null);
    form.resetFields();
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingPlan) {
        await updatePlanMutation.mutateAsync({
          id: editingPlan.id,
          data: values,
        });
        message.success('套餐已更新');
      } else {
        await createPlanMutation.mutateAsync(values);
        message.success('套餐已创建');
      }
      closeModal();
    } catch (error) {
      if (error instanceof Error && error.message) {
        message.error(error.message);
      }
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deletePlanMutation.mutateAsync(id);
      message.success('套餐已删除');
    } catch (error) {
      message.error((error as Error).message || '删除失败');
    }
  };

  const columns = [
    {
      title: '套餐名称',
      dataIndex: 'name',
      key: 'name',
      render: (value: string, record: QuotaPlan) => (
        <Space>
          <Typography.Text strong>{value}</Typography.Text>
          <Tag color={record.status === 'ACTIVE' ? 'success' : 'default'}>
            {record.status === 'ACTIVE' ? '启用中' : '已禁用'}
          </Tag>
        </Space>
      ),
    },
    {
      title: 'QPS',
      dataIndex: 'qps_limit',
      key: 'qps_limit',
      width: 100,
      render: (value: number) => formatNumber(value),
    },
    {
      title: '日配额',
      dataIndex: 'daily_limit',
      key: 'daily_limit',
      render: (value: number) => formatNumber(value),
    },
    {
      title: '月配额',
      dataIndex: 'monthly_limit',
      key: 'monthly_limit',
      render: (value: number) => formatNumber(value),
    },
    {
      title: 'AppKey 上限',
      dataIndex: 'max_app_keys',
      key: 'max_app_keys',
      render: (value: number) => (value < 0 ? '不限' : formatNumber(value)),
    },
    {
      title: '绑定租户',
      dataIndex: 'binding_count',
      key: 'binding_count',
      width: 120,
      render: (value?: number) => formatNumber(value ?? 0),
    },
    {
      title: '创建时间',
      dataIndex: 'create_time',
      key: 'create_time',
      width: 180,
      render: (value?: string) => (value ? formatTimestamp(value) : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_: unknown, record: QuotaPlan) => (
        <Space>
          <Button type="link" onClick={() => openEditModal(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定删除该套餐？"
            description={(record.binding_count ?? 0) > 0 ? '该套餐已绑定租户，后端会拒绝删除。' : '删除后会软禁用该套餐。'}
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="link" danger loading={deletePlanMutation.isPending}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="套餐管理"
        subtitle="维护平台套餐模板，供新租户绑定或后续调整配额"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            新建套餐
          </Button>
        }
      />

      <Card variant="borderless">
        <Table
          rowKey="id"
          columns={columns}
          dataSource={plans ?? []}
          loading={isLoading}
          locale={{ emptyText: <Empty description="暂无套餐数据" /> }}
          pagination={false}
        />
      </Card>

      <Modal
        title={editingPlan ? '编辑套餐' : '新建套餐'}
        open={modalOpen}
        onCancel={closeModal}
        onOk={handleSubmit}
        confirmLoading={createPlanMutation.isPending || updatePlanMutation.isPending}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="套餐名称" rules={[{ required: true, message: '请输入套餐名称' }]}>
            <Input placeholder="例如 Free / Pro / Enterprise" />
          </Form.Item>
          <Form.Item
            name="qps_limit"
            label="QPS 上限"
            rules={[
              { required: true, message: '请输入 QPS 上限' },
              { type: 'number', min: 1, message: 'QPS 必须大于 0' },
            ]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="daily_limit"
            label="日配额"
            rules={[
              { required: true, message: '请输入日配额' },
              { type: 'number', min: 1, message: '日配额必须大于 0' },
            ]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="monthly_limit"
            label="月配额"
            rules={[
              { required: true, message: '请输入月配额' },
              { type: 'number', min: 1, message: '月配额必须大于 0' },
            ]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="max_app_keys"
            label="AppKey 上限"
            tooltip="-1 表示不限"
            rules={[
              { required: true, message: '请输入 AppKey 上限' },
              {
                validator: (_, value?: number) => {
                  if (typeof value !== 'number' || value === -1 || value > 0) return Promise.resolve();
                  return Promise.reject(new Error('AppKey 上限必须大于 0，或填写 -1 表示不限'));
                },
              },
            ]}
          >
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
