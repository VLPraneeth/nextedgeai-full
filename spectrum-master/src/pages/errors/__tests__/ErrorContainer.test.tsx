//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import ErrorContainer from 'pages/errors/ErrorContainer';
import { render, renderWithRouter, screen } from 'tests/helpers';

test('Render the ErrorContainer path container, 404 if location not specified', async () => {
  const { container } = render(<ErrorContainer />);
  expect(await container.querySelector('div')).toBeDefined();
  expect(await screen.findByText('404')).toBeInTheDocument();
});

test('Render the ErrorContainer with error ui', async () => {
  renderWithRouter(<ErrorContainer />, {
    route: '/error-ui',
  });
  expect(await screen.findByText('Oops!')).toBeInTheDocument();
});

test('Render the ErrorContainer with error 404', async () => {
  renderWithRouter(<ErrorContainer />, {
    route: '/error-404',
  });
  expect(await screen.findByText('404')).toBeInTheDocument();
});

test('Render the ErrorContainer with error 504', async () => {
  renderWithRouter(<ErrorContainer />, {
    route: '/error-504',
  });
  expect(await screen.findByText('Scheduled Maintenance')).toBeInTheDocument();
});
