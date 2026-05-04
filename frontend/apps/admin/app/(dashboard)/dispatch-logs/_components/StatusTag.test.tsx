import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { DispatchStatus } from '@lazyday/types';
import { dispatchStatusTagProps, StatusTag } from './StatusTag';

describe('StatusTag', () => {
  it('renders stable tags for all dispatch statuses', () => {
    const statuses: DispatchStatus[] = ['pending', 'sent', 'acked', 'failed', 'timeout'];

    const { asFragment } = render(
      <>
        {statuses.map((status) => (
          <StatusTag key={status} status={status} />
        ))}
      </>,
    );

    expect(Object.keys(dispatchStatusTagProps)).toEqual(statuses);
    expect(asFragment()).toMatchSnapshot();
  });
});
