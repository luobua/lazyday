'use client';

import { App, Form, Input, InputNumber, Modal } from 'antd';
import { usePostDispatch, DISPATCH_LOGS_KEY } from '@/hooks/use-dispatch-logs';
import { useQueryClient } from '@tanstack/react-query';

export function TestDispatchModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm<{ tenantId: number; payload: string }>();
  const postDispatch = usePostDispatch();
  const queryClient = useQueryClient();
  const { message } = App.useApp();

  const submit = async () => {
    const values = await form.validateFields();
    let payload: unknown;
    try {
      payload = JSON.parse(values.payload);
    } catch {
      form.setFields([{ name: 'payload', errors: ['JSON 不合法'] }]);
      return;
    }
    const result = await postDispatch.mutateAsync({ tenantId: values.tenantId, payload });
    message.success(`已下发，msgId=${result.msgId}`);
    queryClient.invalidateQueries({ queryKey: DISPATCH_LOGS_KEY });
    form.resetFields();
    onClose();
  };

  return (
    <Modal
      title="测试下发"
      width={520}
      open={open}
      onCancel={onClose}
      onOk={submit}
      confirmLoading={postDispatch.isPending}
      okButtonProps={{ disabled: postDispatch.isPending }}
    >
      <Form form={form} layout="vertical" initialValues={{ payload: '{"hello":"edge"}' }}>
        <Form.Item name="tenantId" label="tenantId" rules={[{ required: true, message: '请输入 tenantId' }]}>
          <InputNumber min={1} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="payload" label="payload" rules={[{ required: true, message: '请输入 payload JSON' }]}>
          <Input.TextArea rows={8} placeholder='{"hello": "edge"}' />
        </Form.Item>
      </Form>
    </Modal>
  );
}
