//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Error404 from 'pages/errors/Error404';
import * as UserActions from 'store/user/actions';
import { screen, render } from 'tests/helpers';

test('Render the Error404 text', async () => {
  render(<Error404 />);
  expect(await screen.findByText('404')).toBeInTheDocument();
  expect(await screen.findByText("That page can't be found!")).toBeInTheDocument();
});

test('Error 404 hides the breadcrumbs', () => {
  const spyHideBreadcrumbs = jest.spyOn(UserActions, 'hideBreadcrumbs');
  const { unmount } = render(<Error404 />);
  expect(spyHideBreadcrumbs).toBeCalledWith(true);
  unmount();
  expect(spyHideBreadcrumbs).toBeCalledWith(false);
});

test('Error 404 show the breadcrumbs on umount', () => {
  const spyHideBreadcrumbs = jest.spyOn(UserActions, 'hideBreadcrumbs');
  const { unmount } = render(<Error404 />);
  unmount();
  expect(spyHideBreadcrumbs).toBeCalledWith(false);
});
