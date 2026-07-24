//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button } from 'antd';

import { navigateTo } from 'utils/AppUtil';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';

import AuthenticationWrapper from './AuthenticationWrapper';

const tn = tNamespaced('Login');

function PasswordResetSuccess() {
  const login = () => {
    navigateTo(RouteConstants.LOGIN);
  };

  return (
    <AuthenticationWrapper className="forgot-password-content" footer="">
      <div className="authentication-title">{tn('password_reset_success')}</div>
      <div className="description">{tn('password_reset_login_with_new')}</div>
      <div className="action-container">
        <Button className="forgot-password-button" type="primary" onClick={login}>
          {tn('login')}
        </Button>
      </div>
    </AuthenticationWrapper>
  );
}

export default PasswordResetSuccess;
