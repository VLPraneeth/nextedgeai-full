//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Error504 from 'pages/errors/Error504';
import * as UserActions from 'store/user/actions';
import { screen, render } from 'tests/helpers';

test('Render the Error504 text', async () => {
  render(<Error504 />);
  expect(await screen.findByText('Scheduled Maintenance')).toBeInTheDocument();
});

test('Error 504 hides the breadcrumbs', () => {
  const spyHideBreadcrumbs = jest.spyOn(UserActions, 'hideBreadcrumbs');
  const { unmount } = render(<Error504 />);
  expect(spyHideBreadcrumbs).toBeCalledWith(true);
  unmount();
  expect(spyHideBreadcrumbs).toBeCalledWith(false);
});

test('Error 504 show the breadcrumbs on umount', () => {
  const spyHideBreadcrumbs = jest.spyOn(UserActions, 'hideBreadcrumbs');
  const { unmount } = render(<Error504 />);
  unmount();
  expect(spyHideBreadcrumbs).toBeCalledWith(false);
});
