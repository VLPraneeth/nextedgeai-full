//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate } from '@reach/router';
import { useEffect, useState } from 'react';

import Button from 'components/Button';
import InlineMessage from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { getProfile, logout, resetPassword as resetPasswordAction } from 'store/user/thunks';
import { PasswordResetStatusOptions } from 'store/user/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';

import AuthenticationWrapper from './AuthenticationWrapper';

import './UpdatePassword.less';

const tn = tNamespaced('UpdatePassword');

const ResetPassword = (path: any) => {
  const dispatch = useEnhancedDispatch();

  const status = useEnhancedSelector((state) => state.user.status);
  const errorMessages = useEnhancedSelector((state) => state.user.errorMessages);

  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [userId, setUserId] = useState('');

  const [validationState, setValidationState] = useState({
    passwordField: '',
    confirmPasswordField: '',
  });

  const onPasswordChange = (event: any) => setPassword(event.target.value);

  const onPasswordConfirmChange = (event: any) => setPasswordConfirm(event.currentTarget.value);

  useEffect(() => {
    // Get the userId to use the network request
    dispatch(getProfile()).then((result) => {
      const userData = result.payload.user;

      // If the user navigated here manually but their password doesn't need to
      // be reset, send them home. They can reset their password anytime from
      // the profile page.
      if (!userData.passwordExpired) {
        navigate(RouteConstants.HOME);
      } else {
        setUserId(userData.id);
      }
    });
  }, [dispatch]);

  const savePassword = async (e: any) => {
    e.preventDefault();
    setValidationState({ passwordField: '', confirmPasswordField: '' });

    // Validate inputs
    let canSubmit = true;
    const validation = { passwordField: '', confirmPasswordField: '' };

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
      if (userId) {
        await dispatch(resetPasswordAction({ newPwd: password, userId }));
      } else {
        // We should always have the userId since we fetch it on mount but if it
        // failed for some reason we log the user out.
        dispatch(logout());
      }
    }
  };

  useEffect(() => {
    if (status === PasswordResetStatusOptions.success) {
      navigate(RouteConstants.PASSWORD_RESET_SUCCESS);
    }
  }, [status]);

  return (
    <AuthenticationWrapper footer={false} className="update-password">
      <h1 className="update-password__title authentication-title">{tn('reset_title')}</h1>

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

export default ResetPassword;
