'use client';

import React, { useState } from 'react';
import { App, Button, Card, Descriptions, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import { PageHeader } from '@lazyday/ui';
import { formatNumber, formatTimestamp } from '@lazyday/utils';
import type { AdminTenantSummary, OverrideQuotaRequest, QuotaPlan } from '@lazyday/types';
import { useAdminPlans } from '@/hooks/use-plans';
import {
  type AdminTenantQuery,
  useAdminTenantDetail,
  useAdminTenants,
  useResumeTenant,
  useSuspendTenant,
  useUpdateTenantQuota,
} from '@/hooks/use-admin-tenants';

interface FilterValues {
  keyword?: string;
  status?: string;
}

export default function TenantsPage() {
  const [form] = Form.useForm<FilterValues>();
  const [quotaForm] = Form.useForm<OverrideQuotaRequest>();
  const [query, setQuery] = useState<AdminTenantQuery>({ page: 0, size: 20 });
  const [detailId, setDetailId] = useState<number | undefined>();
  const [quotaTenant, setQuotaTenant] = useState<AdminTenantSummary | null>(null);
  const { message } = App.useApp();

  const { data: tenantsPage, isLoading } = useAdminTenants(query);
  const { data: detail, isLoading: detailLoading } = useAdminTenantDetail(detailId);
  const { data: plans } = useAdminPlans();
  const suspendMutation = useSuspendTenant();
  const resumeMutation = useResumeTenant();
  const updateQuotaMutation = useUpdateTenantQuota();

  const handleSearch = (values: FilterValues) => {
    setQuery({ page: 0, size: query.size, keyword: values.keyword, status: values.status });
  };

  const openQuotaModal = (tenant: AdminTenantSummary) => {
    setQuotaTenant(tenant);
    quotaForm.setFieldsValue({ plan_id: tenant.plan_id });
  };

  const handleStatusChange = (tenant: AdminTenantSummary) => {
    const action = tenant.status === 'SUSPENDED' ? resumeMutation : suspendMutation;
    const label = tenant.status === 'SUSPENDED' ? '恢复' : '暂停';
    Modal.confirm({
      title: `确认${label}租户 ${tenant.name}？`,
      content: tenant.status === 'SUSPENDED' ? undefined : '暂停后该租户的所有 API 请求将返回 403',
      okText: label,
      cancelText: '取消',
      onOk: async () => {
        await action.mutateAsync(tenant.id);
        message.success(`租户已${label}`);
      },
    });
  };

  const handleQuotaSubmit = async () => {
    if (!quotaTenant) return;
    try {
      const values = await quotaForm.validateFields();
      await updateQuotaMutation.mutateAsync({ id: quotaTenant.id, data: values });
      message.success('租户套餐已更新');
      setQuotaTenant(null);
      quotaForm.resetFields();
    } catch (error) {
      if (error instanceof Error) message.error(error.message);
    }
  };

  const columns = [
    {
      title: '租户',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: AdminTenantSummary) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{name}</Typography.Text>
          <Typography.Text type="secondary">{record.email || '-'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: string) => <Tag color={status === 'ACTIVE' ? 'success' : 'error'}>{status === 'ACTIVE' ? '活跃' : '已暂停'}</Tag>,
    },
    {
      title: '套餐',
      dataIndex: 'plan_name',
      key: 'plan_name',
      width: 160,
      render: (value?: string) => value || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'created_time',
      key: 'created_time',
      width: 180,
      render: (value?: string) => (value ? formatTimestamp(value) : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_: unknown, record: AdminTenantSummary) => (
        <Space>
          <Button type="link" onClick={() => setDetailId(record.id)}>详情</Button>
          <Button type="link" onClick={() => handleStatusChange(record)}>
            {record.status === 'SUSPENDED' ? '恢复' : '暂停'}
          </Button>
          <Button type="link" onClick={() => openQuotaModal(record)}>改套餐</Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="租户管理" subtitle="查看和管理所有租户" />

      <Card variant="borderless" style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline" onFinish={handleSearch}>
          <Form.Item name="keyword">
            <Input.Search placeholder="搜索名称或邮箱" allowClear onSearch={() => form.submit()} />
          </Form.Item>
          <Form.Item name="status">
            <Select
              placeholder="全部状态"
              allowClear
              style={{ width: 140 }}
              options={[
                { label: '活跃', value: 'ACTIVE' },
                { label: '已暂停', value: 'SUSPENDED' },
              ]}
            />
          </Form.Item>
          <Button type="primary" htmlType="submit">查询</Button>
        </Form>
      </Card>

      <Card variant="borderless">
        <Table
          rowKey="id"
          columns={columns}
          dataSource={tenantsPage?.list ?? []}
          loading={isLoading}
          scroll={{ x: 900 }}
          pagination={{
            current: (tenantsPage?.page ?? 0) + 1,
            pageSize: tenantsPage?.size ?? query.size,
            total: tenantsPage?.total ?? 0,
            onChange: (page, size) => setQuery({ ...query, page: page - 1, size }),
          }}
        />
      </Card>

      <Drawer
        title="租户详情"
        open={!!detailId}
        onClose={() => setDetailId(undefined)}
        width={560}
        loading={detailLoading}
      >
        {detail && (
          <Space direction="vertical" style={{ width: '100%' }} size="large">
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="名称">{detail.name}</Descriptions.Item>
              <Descriptions.Item label="邮箱">{detail.email || '-'}</Descriptions.Item>
              <Descriptions.Item label="状态">{detail.status}</Descriptions.Item>
              <Descriptions.Item label="套餐">{detail.plan_name || '-'}</Descriptions.Item>
              <Descriptions.Item label="QPS">{formatNumber(detail.qps_limit ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="日配额">{formatNumber(detail.daily_limit ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="月配额">{formatNumber(detail.monthly_limit ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="AppKey 上限">{detail.max_app_keys === -1 ? '不限' : formatNumber(detail.max_app_keys ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="今日用量">{formatNumber(detail.daily_usage ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="本月用量">{formatNumber(detail.monthly_usage ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="AppKey 数">{formatNumber(detail.app_key_count ?? 0)}</Descriptions.Item>
              <Descriptions.Item label="管理员邮箱">{detail.tenant_admin_emails?.join(', ') || '-'}</Descriptions.Item>
            </Descriptions>
          </Space>
        )}
      </Drawer>

      <Modal
        title={`修改套餐${quotaTenant ? `：${quotaTenant.name}` : ''}`}
        open={!!quotaTenant}
        onCancel={() => setQuotaTenant(null)}
        onOk={handleQuotaSubmit}
        confirmLoading={updateQuotaMutation.isPending}
      >
        <Form form={quotaForm} layout="vertical">
          <Form.Item name="plan_id" label="套餐" rules={[{ required: true, message: '请选择套餐' }]}>
            <Select
              options={(plans ?? []).map((plan: QuotaPlan) => ({ label: plan.name, value: plan.id }))}
              placeholder="选择套餐"
            />
          </Form.Item>
          <Form.Item name="custom_qps_limit" label="自定义 QPS">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="留空使用套餐默认值" />
          </Form.Item>
          <Form.Item name="custom_daily_limit" label="自定义日配额">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="留空使用套餐默认值" />
          </Form.Item>
          <Form.Item name="custom_monthly_limit" label="自定义月配额">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="留空使用套餐默认值" />
          </Form.Item>
          <Form.Item name="custom_max_app_keys" label="自定义 AppKey 上限" tooltip="-1 表示不限">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
