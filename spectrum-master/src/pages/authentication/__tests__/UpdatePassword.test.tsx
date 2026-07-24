//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate } from '@reach/router';

import UpdatePassword from 'pages/authentication/UpdatePassword';
import { mockedAjaxUtils, render, screen, userEvent } from 'tests/helpers';

jest.mock('utils/AjaxUtil');
const ajaxMock = mockedAjaxUtils();
ajaxMock.post.mockImplementation(() => Promise.resolve());

jest.mock('@reach/router', () => ({ navigate: jest.fn() }));

describe('UpdatePassword', () => {
  afterEach(() => jest.clearAllMocks());

  it('Allows user to fill out form to set password', async () => {
    render(<UpdatePassword invitationId="123" />, { testState: { user: { status: '', errorMessages: [] } } });

    await userEvent.type(screen.getByLabelText('Password'), 'new');
    await userEvent.type(screen.getByLabelText('Confirm Password'), 'new');
    await userEvent.click(screen.getByText('Update'));

    expect(ajaxMock.post).toHaveBeenCalledWith('/arcade/api/v1/user/setpassword/123', { password: 'new' });
    expect(ajaxMock.post).toHaveBeenCalledTimes(1);
  });

  it('Requires values for both fields to submit', async () => {
    render(<UpdatePassword invitationId="123" />, { testState: { user: { status: '', errorMessages: [] } } });

    await userEvent.click(screen.getByText('Update'));
    expect(screen.getAllByText('This is required')).toHaveLength(2);

    await userEvent.type(screen.getByLabelText('Password'), 'new');
    await userEvent.click(screen.getByText('Update'));
    expect(screen.getByText('Passwords must match')).toBeVisible();

    expect(ajaxMock.post).toHaveBeenCalledTimes(0);
  });

  it('Displays errors when present', (done) => {
    render(<UpdatePassword invitationId="123" />, {
      testState: { user: { status: '', errorMessages: ['Test Error'] } },
    });

    setTimeout(() => {
      expect(screen.getByText('Test Error')).toBeVisible();
      done();
    }, 100);
  });

  it('Redirects to confirmation page when status is success', () => {
    render(<UpdatePassword invitationId="123" />, {
      testState: { user: { status: 'success', errorMessages: ['Test Error'] } },
    });

    expect(navigate).toHaveBeenCalledTimes(1);
  });
});
