'use client';

import React, { Suspense, useMemo, useState } from 'react';
import { Button, Card, DatePicker, Empty, Form, Grid, Input, InputNumber, Select, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { ThunderboltOutlined } from '@ant-design/icons';
import { PageHeader } from '@lazyday/ui';
import { formatTimestamp } from '@lazyday/utils';
import type { DispatchLog, DispatchLogQuery, DispatchStatus } from '@lazyday/types';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useDispatchLog, useDispatchLogs } from '@/hooks/use-dispatch-logs';
import { StatusTag } from './_components/StatusTag';
import { DispatchDetailDrawer } from './_components/DispatchDetailDrawer';
import { TestDispatchModal } from './_components/TestDispatchModal';

const { RangePicker } = DatePicker;

interface FilterValues {
  tenantId?: number;
  status?: DispatchStatus[];
  createdRange?: [unknown, unknown];
  msgId?: string;
}

export default function DispatchLogsPage() {
  return (
    <Suspense fallback={null}>
      <DispatchLogsContent />
    </Suspense>
  );
}

function DispatchLogsContent() {
  const [form] = Form.useForm<FilterValues>();
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();
  const screens = Grid.useBreakpoint();
  const [detailMsgId, setDetailMsgId] = useState<string>();
  const [modalOpen, setModalOpen] = useState(false);

  const query = useMemo(() => queryFromSearch(searchParams), [searchParams]);
  const { data, isLoading } = useDispatchLogs(query);
  const { data: detail, isLoading: detailLoading } = useDispatchLog(detailMsgId);

  const columns: ColumnsType<DispatchLog> = [
    {
      title: 'msgId',
      dataIndex: 'msgId',
      width: 200,
      render: (value: string) => <Typography.Text copyable={{ text: value }} code>{value}</Typography.Text>,
    },
    { title: '租户', dataIndex: 'tenantId', width: 100 },
    { title: '类型', dataIndex: 'type', width: 130, render: (value: string) => <Tag>{value}</Tag> },
    { title: '状态', dataIndex: 'status', width: 110, render: (value: DispatchStatus) => <StatusTag status={value} /> },
    { title: '创建时间', dataIndex: 'createdTime', width: 170, render: (value: string) => formatTimestamp(value) },
    { title: 'ACK 时间', dataIndex: 'ackedTime', width: 170, render: (value?: string | null) => value ? formatTimestamp(value) : '-' },
    {
      title: '错误',
      dataIndex: 'lastError',
      width: 200,
      render: (value?: string | null) => value ? (
        <Tooltip title={value}><Typography.Text ellipsis style={{ maxWidth: 180 }}>{value}</Typography.Text></Tooltip>
      ) : '-',
    },
    {
      title: '操作',
      width: 80,
      render: (_, record) => <Button type="link" onClick={() => setDetailMsgId(record.msgId)}>详情</Button>,
    },
  ];

  const submit = (values: FilterValues) => {
    const params = new URLSearchParams();
    if (values.tenantId) params.set('tenantId', String(values.tenantId));
    values.status?.forEach((status) => params.append('status', status));
    const [from, to] = values.createdRange ?? [];
    if (isDayjs(from)) params.set('from', from.toISOString());
    if (isDayjs(to)) params.set('to', to.toISOString());
    if (values.msgId) params.set('msgId', values.msgId);
    params.set('page', '0');
    params.set('size', String(query.size ?? 20));
    router.push(`${pathname}?${params.toString()}`);
  };

  const reset = () => {
    form.resetFields();
    router.push(pathname);
  };

  if (screens.xs || screens.sm || screens.md) {
    return <Empty description="请使用桌面端访问" />;
  }

  return (
    <>
      <PageHeader
        title="Dispatch Logs"
        subtitle="Backend → Edge 下发轨迹"
        extra={<Button type="primary" icon={<ThunderboltOutlined />} onClick={() => setModalOpen(true)}>测试下发</Button>}
      />

      <Card variant="borderless" style={{ marginBottom: 16 }}>
        <Form
          form={form}
          layout="inline"
          onFinish={submit}
          initialValues={{
            tenantId: query.tenantId,
            status: query.status,
            createdRange: query.from && query.to ? [dayjs(query.from), dayjs(query.to)] : undefined,
            msgId: query.msgId,
          }}
        >
          <Form.Item name="tenantId">
            <InputNumber min={1} placeholder="tenantId" />
          </Form.Item>
          <Form.Item name="status">
            <Select
              mode="multiple"
              allowClear
              placeholder="状态"
              style={{ width: 220 }}
              options={[
                { label: '等待发送', value: 'pending' },
                { label: '已发送', value: 'sent' },
                { label: '已确认', value: 'acked' },
                { label: '失败', value: 'failed' },
                { label: '超时', value: 'timeout' },
              ]}
            />
          </Form.Item>
          <Form.Item name="createdRange">
            <RangePicker showTime />
          </Form.Item>
          <Form.Item name="msgId">
            <Input.Search placeholder="msgId" allowClear onSearch={() => form.submit()} />
          </Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">查询</Button>
            <Button onClick={reset}>重置</Button>
          </Space>
        </Form>
      </Card>

      <Card variant="borderless">
        <Table
          rowKey="msgId"
          columns={columns}
          dataSource={data?.list ?? []}
          loading={isLoading}
          scroll={{ x: 1100 }}
          onRow={(record) => ({ onDoubleClick: () => setDetailMsgId(record.msgId), style: { cursor: 'pointer' } })}
          pagination={{
            current: (data?.page ?? 0) + 1,
            pageSize: data?.size ?? query.size ?? 20,
            total: data?.total ?? 0,
            onChange: (page, size) => {
              const params = new URLSearchParams(searchParams.toString());
              params.set('page', String(page - 1));
              params.set('size', String(size));
              router.push(`${pathname}?${params.toString()}`);
            },
          }}
        />
      </Card>

      <DispatchDetailDrawer
        open={!!detailMsgId}
        loading={detailLoading}
        log={detail}
        onClose={() => setDetailMsgId(undefined)}
      />
      <TestDispatchModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </>
  );
}

function queryFromSearch(searchParams: URLSearchParams): DispatchLogQuery {
  const tenantId = searchParams.get('tenantId');
  return {
    tenantId: tenantId ? Number(tenantId) : undefined,
    status: searchParams.getAll('status') as DispatchStatus[],
    from: searchParams.get('from') || undefined,
    to: searchParams.get('to') || undefined,
    msgId: searchParams.get('msgId') || undefined,
    page: Number(searchParams.get('page') ?? 0),
    size: Number(searchParams.get('size') ?? 20),
  };
}

function isDayjs(value: unknown): value is Dayjs {
  return dayjs.isDayjs(value);
}
