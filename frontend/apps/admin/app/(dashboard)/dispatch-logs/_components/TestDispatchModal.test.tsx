import { App } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { dispatchApi } from '@lazyday/api-client';
import { TestDispatchModal } from './TestDispatchModal';

vi.mock('@lazyday/api-client', () => ({
  dispatchApi: {
    postDispatch: vi.fn(),
  },
}));

function renderModal() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <App>
        <TestDispatchModal open onClose={() => {}} />
      </App>
    </QueryClientProvider>,
  );
}

describe('TestDispatchModal', () => {
  it('shows a form error and does not request dispatch for invalid JSON', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText('tenantId'), '1');
    await user.clear(screen.getByLabelText('payload'));
    fireEvent.change(screen.getByLabelText('payload'), { target: { value: '{bad-json' } });
    await user.click(screen.getByRole('button', { name: 'OK' }));

    expect(await screen.findByText('JSON 不合法')).toBeInTheDocument();
    expect(dispatchApi.postDispatch).not.toHaveBeenCalled();
  });
});
