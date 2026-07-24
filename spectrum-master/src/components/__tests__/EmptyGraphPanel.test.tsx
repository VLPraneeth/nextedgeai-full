//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render, screen } from '@testing-library/react';

import EmptyGraphPanel from 'components/EmptyGraphPanel';

it('EmptyGraphContent renders action text', async () => {
  render(<EmptyGraphPanel actionText="Create draft" />);
  expect((await screen.findByText('Create draft')).textContent).toMatch(/Create draft/);
});

it('EmptyGraphContent renders empty text', async () => {
  render(
    <EmptyGraphPanel actionText="Create draft">
      <span>empty panel content</span>
    </EmptyGraphPanel>
  );
  expect((await screen.findByText('empty panel content')).textContent).toMatch(/empty panel content/);
});
