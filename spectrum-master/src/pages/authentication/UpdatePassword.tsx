//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate } from '@reach/router';
import { useEffect, useState } from 'react';

import Button from 'components/Button';
import InlineMessage from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { setPassword as setPasswordAction } from 'store/user/thunks';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';

import AuthenticationWrapper from './AuthenticationWrapper';

import 'antd/dist/antd.css';
import './UpdatePassword.less';

const tn = tNamespaced('UpdatePassword');

interface ValidationState {
  passwordField?: string;
  confirmPasswordField?: string;
}

export interface UpdatePasswordProps {
  invitationId: string;
}

const UpdatePassword = ({ invitationId }: UpdatePasswordProps) => {
  const dispatch = useEnhancedDispatch();

  const status = useEnhancedSelector((state) => state.user.status);
  const errorMessages = useEnhancedSelector((state) => state.user.errorMessages);

  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');

  const [validationState, setValidationState] = useState<ValidationState>({
    passwordField: '',
    confirmPasswordField: '',
  });

  const onPasswordChange = (event: any) => setPassword(event.target.value);

  const onPasswordConfirmChange = (event: any) => setPasswordConfirm(event.currentTarget.value);

  const savePassword = (e: any) => {
    e.preventDefault();
    setValidationState({});

    // Validate inputs
    let canSubmit = true;
    const validation: ValidationState = {};

    if (!password) {
      validation.passwordField = tc('required');
      canSubmit = false;
    }
    if (!passwordConfirm) {
      validation.confirmPasswordField = tc('required');
      canSubmit = false;
    }
    if (password !== passwordConfirm) {
      validation.confirmPasswordField = tn('match_error');
      canSubmit = false;
    }

    if (!canSubmit) {
      setValidationState(validation);
    } else {
      dispatch(setPasswordAction({ invitationId, password }));
    }
  };

  useEffect(() => {
    if (status === 'success') {
      navigate(RouteConstants.PASSWORD_RESET_SUCCESS);
    }
  }, [status]);

  return (
    <AuthenticationWrapper className="update-password">
      <h1 className="update-password__title authentication-title">{tn('title')}</h1>

      {errorMessages.map((errorMessage, i) => (
        <InlineMessage key={i} type="error" title={errorMessage}>
          {errorMessage}
        </InlineMessage>
      ))}

      <form onSubmit={savePassword}>
        <InputWithLabel
          className="update-password__input"
          help={validationState.passwordField}
          id="password-field"
          label={tn('password')}
          onChange={onPasswordChange}
          type="password"
          validateStatus={validationState.passwordField ? 'error' : 'success'}
          value={password}
          visibilityToggle={false}
        />
        <InputWithLabel
          className="update-password__input"
          help={validationState.confirmPasswordField}
          id="confirm-password-field"
          label={tn('confirm_password')}
          onChange={onPasswordConfirmChange}
          type="password"
          validateStatus={validationState.confirmPasswordField ? 'error' : 'success'}
          value={passwordConfirm}
          visibilityToggle={false}
        />

        <Button className="update-password__submit" htmlType="submit" type="primary">
          {tc('update')}
        </Button>
      </form>
    </AuthenticationWrapper>
  );
};

export default UpdatePassword;
