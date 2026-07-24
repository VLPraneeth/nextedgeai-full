//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import ErrorUi from 'pages/errors/ErrorUi';
import { render, screen } from 'tests/helpers';

test('Render the ErrorUi text', async () => {
  render(<ErrorUi />);
  expect(await screen.findByText('Oops!')).toBeInTheDocument();
  expect(
    await screen.findByText('An unexpected error occurred, and details have been sent to NextEdge AI.')
  ).toBeInTheDocument();
});
