import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EdgeStatusBadge } from './EdgeStatusBadge';
import { useEdgeStatus } from '@/hooks/use-edge-status';

vi.mock('@/hooks/use-edge-status', () => ({
  useEdgeStatus: vi.fn(),
}));

const mockUseEdgeStatus = vi.mocked(useEdgeStatus);

describe('EdgeStatusBadge', () => {
  it('renders connected, stale, and disconnected states with aria-live', () => {
    mockUseEdgeStatus.mockReturnValue({
      data: { connected: true, sessionCount: 1, lastSeenAgoMs: 3_000 },
      isLoading: false,
    } as ReturnType<typeof useEdgeStatus>);

    const { rerender } = render(<EdgeStatusBadge />);
    expect(screen.getByText(/Edge 在线/).closest('.ant-tag')).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByText(/1 个会话/)).toBeInTheDocument();

    mockUseEdgeStatus.mockReturnValue({
      data: { connected: true, sessionCount: 1, lastSeenAgoMs: 25_000 },
      isLoading: false,
    } as ReturnType<typeof useEdgeStatus>);
    rerender(<EdgeStatusBadge />);
    expect(screen.getByText('Edge 心跳延迟 (25s)').closest('.ant-tag')).toHaveAttribute('aria-live', 'polite');

    mockUseEdgeStatus.mockReturnValue({
      data: { connected: false, sessionCount: 0, lastSeenAgoMs: null },
      isLoading: false,
    } as ReturnType<typeof useEdgeStatus>);
    rerender(<EdgeStatusBadge />);
    expect(screen.getByText('Edge 离线').closest('.ant-tag')).toHaveAttribute('aria-live', 'polite');
  });
});
