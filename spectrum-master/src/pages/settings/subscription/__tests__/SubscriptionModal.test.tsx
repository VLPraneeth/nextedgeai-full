//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { render, screen, userEvent } from 'tests/helpers';

import SubscriptionModal from '../SubscriptionModal';

it('SubscriptionModal renders the first and last name fields', async () => {
  render(<SubscriptionModal />);
  expect(await screen.findByPlaceholderText('First')).toBeInTheDocument();
  expect(await screen.findByPlaceholderText('Last')).toBeInTheDocument();
});

it('SubscriptionModal has four types: Produciton, Sandbox, Demo, and Internal', async () => {
  render(<SubscriptionModal />);

  await userEvent.click(await screen.findByText('Production'));
  expect(await screen.findByText('Sandbox')).toBeInTheDocument();
  expect(await screen.findByText('Demo')).toBeInTheDocument();
  expect(await screen.findByText('Internal')).toBeInTheDocument();
});
