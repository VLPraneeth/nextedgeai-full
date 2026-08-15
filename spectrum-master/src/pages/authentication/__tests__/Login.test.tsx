// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { render, fireEvent } from '@testing-library/react';

import Login from 'pages/authentication/Login';
import { tNamespaced } from 'utils/i18nUtil';

jest.mock('pages/authentication/GoogleSignIn', () => () => <div data-testid="google-sign-in" />);

const tn = tNamespaced('Login');

describe('Login Page', () => {
  let googleLogin, login, logout;
  beforeAll(() => {
    googleLogin = jest.fn();
    login = jest.fn();
    logout = jest.fn();
  });

  afterEach(() => {
    const root = document.getElementById('root');
    root?.removeAttribute('data-demo-admin-email');
    root?.removeAttribute('data-demo-admin-password');
    root?.removeAttribute('data-demo-guided-email');
    root?.removeAttribute('data-demo-guided-password');
  });

  it('has a username field that changes value on text entry', () => {
    const { container } = render(<Login logout={logout} login={login} googleLogin={googleLogin} />);
    fireEvent.change(container.querySelector(`input[name="username"]`), {
      target: { value: 'user' },
    });
    expect(container.querySelector(`input[value="user"]`)).toBeInTheDocument();
  });

  it('has a password field that changes value on text entry', () => {
    const { container } = render(<Login logout={logout} login={login} googleLogin={googleLogin} />);
    fireEvent.change(container.querySelector(`input[name="password"]`), {
      target: { value: 'pass' },
    });
    expect(container.querySelector(`input[value="pass"]`)).toBeInTheDocument();
  });

  it('calls logout when logout is passed', () => {
    render(<Login logout={logout} login={login} googleLogin={googleLogin} />);
    expect(logout).toHaveBeenCalled();
  });

  it('calls login when submit is pressed', () => {
    const screen = render(<Login logout={logout} login={login} googleLogin={googleLogin} />);
    const submitButton = screen.getByText(tn('login'));
    fireEvent.click(submitButton);
    expect(login).toHaveBeenCalled();
  });

  it('switches between the runtime demo accounts', () => {
    const root = document.getElementById('root') || document.createElement('div');
    root.id = 'root';
    root.dataset.demoGuidedEmail = 'demo@nextedge.ai';
    root.dataset.demoGuidedPassword = 'guided-password';
    root.dataset.demoAdminEmail = 'admin@nextedge.ai';
    root.dataset.demoAdminPassword = 'admin-password';
    if (!root.parentElement) {
      document.body.appendChild(root);
    }

    const screen = render(<Login logout={logout} login={login} googleLogin={googleLogin} />);

    expect(screen.getByText('demo@nextedge.ai')).toBeInTheDocument();
    expect(screen.getByText('guided-password')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Account type'), { target: { value: 'admin' } });

    expect(screen.getByText('admin@nextedge.ai')).toBeInTheDocument();
    expect(screen.getByText('admin-password')).toBeInTheDocument();

    root.removeAttribute('data-demo-guided-email');
    root.removeAttribute('data-demo-guided-password');
    root.removeAttribute('data-demo-admin-email');
    root.removeAttribute('data-demo-admin-password');
  });
});
