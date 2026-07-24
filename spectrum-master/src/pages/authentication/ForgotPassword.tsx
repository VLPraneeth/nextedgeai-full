//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Link } from '@reach/router';
import { Button, message } from 'antd';
import classNames from 'classnames';
import { ChangeEvent, Dispatch, SetStateAction, useEffect, useState } from 'react';

import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import InputLayout from 'components/layout/InputLayout';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { forgotPassword } from 'store/user/thunks';
import { HTTP } from 'utils/AjaxUtil';
import { navigateTo } from 'utils/AppUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { LOOSE_EMAIL_REGEX } from 'utils/RegexUtil';
import RouteConstants from 'utils/RouteConstants';

import AuthenticationWrapper from './AuthenticationWrapper';

const tn = tNamespaced('Login');

const ForgotPasswordSent = () => {
  const navigateToLogin = () => {
    navigateTo(RouteConstants.LOGIN);
  };

  const user = useEnhancedSelector((state) => state.user);
  const { forgotPasswordHeader: header, forgotPasswordSubheader: subheader } = user;

  return (
    <>
      <div className="authentication-title">{header}</div>
      <div className="description">{subheader}</div>
      <div className="action-container">
        <Button className="forgot-password-button" type="primary" onClick={navigateToLogin}>
          {tn('done')}
        </Button>
      </div>
    </>
  );
};

const ForgotPasswordInputScreen = ({
  setRecoveryEmail,
}: {
  setRecoveryEmail: Dispatch<SetStateAction<string | null>>;
}) => {
  const dispatch = useEnhancedDispatch();

  const [emailInput, setEmailInput] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
  const [buttonEnabled, setButtonEnabled] = useState<boolean>(false);

  const handleForgotPassword = async () => {
    if (!LOOSE_EMAIL_REGEX.test(emailInput.trim())) {
      message.error(tc('email_invalid_with_email', { email: emailInput.trim() }));
      return;
    }
    const forgotPasswordRes = await dispatch(forgotPassword(emailInput));
    if (forgotPasswordRes === HTTP.OK) {
      setRecoveryEmail(emailInput);
    }
    if (forgotPasswordRes !== HTTP.OK) {
      setErrorMessage(forgotPasswordRes);
    }
  };

  const onEmailChange = (event: ChangeEvent<HTMLInputElement>) => {
    setEmailInput(event.target.value);
  };

  useEffect(() => {
    const validateEmail = () => {
      if (emailInput.length > 2) {
        return true;
      }
    };

    if (validateEmail()) {
      setButtonEnabled(true);
    } else {
      setButtonEnabled(false);
    }
  }, [emailInput]);

  return (
    <>
      <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage} />
      <div className="authentication-title">{tn('forgot_password')}</div>
      <InputLayout>
        <InputWithLabel
          label={tn('forgot_password_label')}
          name="email"
          type="email"
          className="username-input"
          placeholder={tn('enter_username')}
          value={emailInput}
          onChange={onEmailChange}
        />
      </InputLayout>
      <div className={classNames('action-container', 'action-container--input-screen')}>
        <Button
          data-testid="forgot-password-button"
          className="forgot-password-button"
          type="primary"
          onClick={handleForgotPassword}
          disabled={!buttonEnabled}>
          {tn('forgot_password_btn_text')}
        </Button>
      </div>
      <Link className="back-to-login" to={RouteConstants.LOGIN}>
        {tn('back_to_login')}
      </Link>
    </>
  );
};

const ForgotPassword = () => {
  const [recoveryEmail, setRecoveryEmail] = useState<string | null>(null);

  return (
    <AuthenticationWrapper className="forgot-password-content" footer="">
      {recoveryEmail ? <ForgotPasswordSent /> : <ForgotPasswordInputScreen setRecoveryEmail={setRecoveryEmail} />}
    </AuthenticationWrapper>
  );
};

export default ForgotPassword;
