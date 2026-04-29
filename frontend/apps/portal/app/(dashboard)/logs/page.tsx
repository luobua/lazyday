'use client';

import React, { useState } from 'react';
import { App, Button, Card, Col, DatePicker, Empty, Form, Input, Row, Select, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { Dayjs } from 'dayjs';
import { DownloadOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { PageHeader } from '@lazyday/ui';
import { formatLatency, formatNumber, formatTimestamp, getStatusCodeColor } from '@lazyday/utils';
import { useCallLogs, useCallLogStats, useExportCallLogs } from '@/hooks/use-logs';
import type { CallLogQuery } from '@lazyday/types';

const DEFAULT_START_TIME = dayjs().subtract(7, 'day').startOf('day').toISOString();
const DEFAULT_END_TIME = dayjs().endOf('day').toISOString();

const STATUS_GROUP_OPTIONS = [
  { label: '全部状态', value: undefined },
  { label: '2xx 成功', value: 2 },
  { label: '4xx 客户端错误', value: 4 },
  { label: '5xx 服务端错误', value: 5 },
];

interface LogFilterValues {
  appKey?: string;
  path?: string;
  statusCodeGroup?: number;
  range?: [Dayjs, Dayjs];
}

export default function LogsPage() {
  const [form] = Form.useForm<LogFilterValues>();
  const [query, setQuery] = useState<CallLogQuery>({
    page: 0,
    size: 20,
    startTime: DEFAULT_START_TIME,
    endTime: DEFAULT_END_TIME,
  });
  const { message } = App.useApp();

  const { data: logsPage, isLoading } = useCallLogs(query);
  const { data: stats, isLoading: statsLoading } = useCallLogStats({
    startTime: query.startTime,
    endTime: query.endTime,
  });
  const exportMutation = useExportCallLogs();

  const handleSearch = (values: LogFilterValues) => {
    const range = values.range;
    setQuery({
      appKey: values.appKey || undefined,
      path: values.path || undefined,
      statusCodeGroup: values.statusCodeGroup,
      startTime: range?.[0]?.startOf('day').toISOString(),
      endTime: range?.[1]?.endOf('day').toISOString(),
      page: 0,
      size: query.size ?? 20,
    });
  };

  const handleReset = () => {
    form.resetFields();
    setQuery({
      page: 0,
      size: query.size ?? 20,
      startTime: DEFAULT_START_TIME,
      endTime: DEFAULT_END_TIME,
    });
  };

  const handleExport = async () => {
    try {
      const blob = await exportMutation.mutateAsync(query);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'call_logs.csv';
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      message.success('CSV 已开始下载');
    } catch (error) {
      message.error((error as Error).message || '导出失败');
    }
  };

  const columns = [
    {
      title: '时间',
      dataIndex: 'request_time',
      key: 'request_time',
      width: 180,
      render: (value: string) => formatTimestamp(value),
    },
    {
      title: '接口路径',
      dataIndex: 'path',
      key: 'path',
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: 'AppKey',
      dataIndex: 'app_key',
      key: 'app_key',
      width: 180,
      render: (value: string) => <Typography.Text>{value}</Typography.Text>,
    },
    {
      title: '方法',
      dataIndex: 'method',
      key: 'method',
      width: 90,
    },
    {
      title: '状态码',
      dataIndex: 'status_code',
      key: 'status_code',
      width: 110,
      render: (value: number) => <Tag color={getStatusCodeColor(value)}>{value}</Tag>,
    },
    {
      title: '延迟',
      dataIndex: 'latency_ms',
      key: 'latency_ms',
      width: 120,
      render: (value: number) => formatLatency(value),
    },
    {
      title: '客户端 IP',
      dataIndex: 'client_ip',
      key: 'client_ip',
      width: 160,
      render: (value?: string) => value || '-',
    },
  ];

  return (
    <>
      <PageHeader
        title="调用日志"
        subtitle="按时间范围、路径和状态快速排查 API 调用情况"
        extra={
          <Button
            icon={<DownloadOutlined />}
            onClick={handleExport}
            loading={exportMutation.isPending}
          >
            导出 CSV
          </Button>
        }
      />

      <Card variant="borderless" style={{ marginBottom: 16 }}>
        <Form form={form} layout="vertical" onFinish={handleSearch}>
          <Row gutter={[16, 8]}>
            <Col xs={24} md={12} lg={6}>
              <Form.Item name="appKey" label="AppKey">
                <Input placeholder="输入 AppKey 过滤" allowClear />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} lg={6}>
              <Form.Item name="path" label="路径关键字">
                <Input placeholder="例如 /ai/chat" allowClear />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} lg={4}>
              <Form.Item name="statusCodeGroup" label="状态码">
                <Select options={STATUS_GROUP_OPTIONS} allowClear placeholder="全部状态" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} lg={8}>
              <Form.Item name="range" label="时间范围">
                <DatePicker.RangePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </Space>
        </Form>
      </Card>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} xl={6}>
          <Card variant="borderless">
            <Statistic
              title="总调用量"
              value={stats?.total ?? 0}
              formatter={(value) => formatNumber(Number(value))}
              loading={statsLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card variant="borderless">
            <Statistic
              title="成功请求"
              value={stats?.success_count ?? 0}
              formatter={(value) => formatNumber(Number(value))}
              loading={statsLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card variant="borderless">
            <Statistic
              title="错误请求"
              value={(stats?.client_error_count ?? 0) + (stats?.server_error_count ?? 0)}
              formatter={(value) => formatNumber(Number(value))}
              loading={statsLoading}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card variant="borderless">
            <Statistic
              title="平均延迟"
              value={stats?.avg_latency_ms ?? 0}
              precision={1}
              suffix="ms"
              loading={statsLoading}
            />
          </Card>
        </Col>
      </Row>

      <Card variant="borderless">
        <Table
          rowKey="id"
          columns={columns}
          dataSource={logsPage?.list ?? []}
          loading={isLoading}
          locale={{ emptyText: <Empty description="当前筛选条件下暂无日志" /> }}
          pagination={{
            current: (logsPage?.page ?? 0) + 1,
            pageSize: logsPage?.size ?? query.size ?? 20,
            total: logsPage?.total ?? 0,
            showSizeChanger: true,
            showTotal: (total) => `共 ${formatNumber(total)} 条`,
            onChange: (page, size) => {
              setQuery((prev) => ({
                ...prev,
                page: page - 1,
                size,
              }));
            },
          }}
        />
      </Card>
    </>
  );
}
