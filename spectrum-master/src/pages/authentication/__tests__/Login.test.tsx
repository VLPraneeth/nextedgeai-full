// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { render, fireEvent } from '@testing-library/react';

import Login from 'pages/authentication/Login';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('Login');

describe('Login Page', () => {
  let login, logout;
  beforeAll(() => {
    login = jest.fn();
    logout = jest.fn();
  });

  it('has a username field that changes value on text entry', () => {
    const { container } = render(<Login logout={logout} login={login} />);
    fireEvent.change(container.querySelector(`input[name="username"]`), {
      target: { value: 'user' },
    });
    expect(container.querySelector(`input[value="user"]`)).toBeInTheDocument();
  });

  it('has a password field that changes value on text entry', () => {
    const { container } = render(<Login logout={logout} login={login} />);
    fireEvent.change(container.querySelector(`input[name="password"]`), {
      target: { value: 'pass' },
    });
    expect(container.querySelector(`input[value="pass"]`)).toBeInTheDocument();
  });

  it('calls logout when logout is passed', () => {
    render(<Login logout={logout} login={login} />);
    expect(logout).toHaveBeenCalled();
  });

  it('calls login when submit is pressed', () => {
    const screen = render(<Login logout={logout} login={login} />);
    const submitButton = screen.getByText(tn('login'));
    fireEvent.click(submitButton);
    expect(login).toHaveBeenCalled();
  });
});
