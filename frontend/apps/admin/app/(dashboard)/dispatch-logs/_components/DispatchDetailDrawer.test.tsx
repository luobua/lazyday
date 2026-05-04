import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { DispatchLog } from '@lazyday/types';
import { DispatchDetailDrawer } from './DispatchDetailDrawer';

vi.mock('@lazyday/utils', () => ({
  formatTimestamp: (value: string) => value,
}));

describe('DispatchDetailDrawer', () => {
  it('renders a dispatch log with the full formatted payload', () => {
    const log: DispatchLog = {
      msgId: '18880001',
      tenantId: 1,
      type: 'CONFIG_UPDATE',
      status: 'acked',
      payload: { hello: 'edge', nested: { enabled: true } },
      createdTime: '2026-05-01T12:00:00Z',
      ackedTime: '2026-05-01T12:00:01Z',
      lastError: null,
    };

    render(<DispatchDetailDrawer open log={log} onClose={() => {}} />);

    expect(screen.getByText('18880001')).toBeInTheDocument();
    expect(screen.getByText('已确认')).toBeInTheDocument();
    expect(screen.getByText(/"hello": "edge"/)).toBeInTheDocument();
    expect(screen.getByText(/"enabled": true/)).toBeInTheDocument();
  });
});
