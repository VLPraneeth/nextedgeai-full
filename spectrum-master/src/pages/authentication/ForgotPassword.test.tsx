import { waitFor } from '@testing-library/react';

import ForgotPassword from 'pages/authentication/ForgotPassword';
import { fireEvent, render, screen, userEvent } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('Login');

describe('ForgotPassword', () => {
  it('has a email field that changes value on text entry', () => {
    const { getByPlaceholderText } = render(<ForgotPassword />);
    fireEvent.change(getByPlaceholderText(tn('enter_username')), {
      target: { value: 'test value' },
    });
    expect(screen.getByDisplayValue('test value')).toBeVisible();
  });

  it('disables button unless emails length is more than 2 characters', async () => {
    const { getByTestId } = render(<ForgotPassword />);
    await userEvent.type(screen.getByPlaceholderText('Enter your email'), 't@');

    const button = getByTestId('forgot-password-button');
    expect(button).toBeDisabled();
  });

  it('enables button if email is more than 2 characters', async () => {
    const { getByTestId } = render(<ForgotPassword />);

    await userEvent.type(screen.getByPlaceholderText('Enter your email'), 'success@gmail.com');

    await waitFor(() => {
      const button = getByTestId('forgot-password-button');
      expect(button).not.toBeDisabled();
    });
  });
});
