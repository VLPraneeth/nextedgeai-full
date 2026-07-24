//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Link } from '@reach/router';
import { Button, Checkbox, Input, Spin } from 'antd';
import cx from 'classnames';
import { ChangeEvent, FormEvent, useEffect, useReducer } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputLayout from 'components/layout/InputLayout';
import { TranslatedText } from 'components/typography';
import useQueryParams from 'hooks/useQueryParams';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants, { ERROR_MESSAGE, REDIRECT_TO, SYNCARI_BASE_URL } from 'utils/RouteConstants';

import AuthenticationWrapper from './AuthenticationWrapper';
import './Login.less';
import { MarketingFallbackContent } from './MarketingFallbackContent';

const { FETCH_STATUS } = AppConstants;
const UPDATE_FIELD = 'UPDATE_FIELD';
const LOCAL_STORAGE_KEY = 'marketing-content';
const privacyPolicyUrl = '/privacy';

if (!localStorage.getItem(LOCAL_STORAGE_KEY)) {
  localStorage.setItem(LOCAL_STORAGE_KEY, '');
}

type InitialState = {
  username: string;
  password: string;
  marketingContentAvailable: boolean;
};

type Action = {
  type: string;
  payload: {
    name: string;
    value: string | boolean;
  };
};

type LoginProps = {
  errorMessage: string;
  fetchingLoginStatus: string;
  login: Function;
  logout: Function;
};

type QueryParams = {
  [REDIRECT_TO]: string;
  [ERROR_MESSAGE]: string;
};

const initialState: InitialState = {
  username: '',
  password: '',
  marketingContentAvailable: false,
};

const reducer = (state: InitialState, action: Action) => {
  switch (action.type) {
    case UPDATE_FIELD: {
      const { name, value } = action.payload;
      return {
        ...state,
        [name]: value,
      };
    }
    default:
      return state;
  }
};

function Login({ errorMessage, fetchingLoginStatus, login, logout }: LoginProps) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const [queryParams] = useQueryParams<QueryParams>();
  const { tn } = useI18nContext();

  const handleOnChange = (evt: ChangeEvent<HTMLInputElement>) => {
    dispatch({
      type: UPDATE_FIELD,
      payload: {
        name: evt.target.name,
        value: evt.target.value,
      },
    });
  };

  const handleSubmit = (evt: FormEvent<HTMLFormElement>) => {
    evt.preventDefault();
    login(state.username, state.password, queryParams[REDIRECT_TO]);
  };

  useEffect(() => {
    logout(true);
  }, [logout]);

  const message =
    errorMessage || (queryParams[ERROR_MESSAGE] && decodeURIComponent(queryParams[ERROR_MESSAGE] as string)) || '';

  const loginContainerClasses = cx(['login-content', 'login-content-with-marketing-content']);

  return (
    <div className="login-container">
      <MarketingContent />
      <AuthenticationWrapper
        className={loginContainerClasses}
        footer={
          <>
            <TranslatedText beDangerous text="demo" />
            <p
              className="privacy-policy-agreement"
              dangerouslySetInnerHTML={{ __html: tn('policy_agreement', { link: privacyPolicyUrl }) }}></p>
          </>
        }>
        <Spin spinning={fetchingLoginStatus === FETCH_STATUS.LOADING} delay={250}>
          <form onSubmit={handleSubmit}>
            <InlineMessage type={InlineMessageTypes.ERROR} title={message} allowMultiline>
              {message}
            </InlineMessage>
            <InputLayout label="Email">
              <Input
                className="username-input"
                name="username"
                placeholder={tn('enter_username')}
                onChange={handleOnChange}
                autoComplete="username"
                required
              />
            </InputLayout>
            <InputLayout className="input-password" label="Password">
              <Input.Password
                visibilityToggle={false}
                name="password"
                className="password-input"
                placeholder={tn('enter_password')}
                onChange={handleOnChange}
                autoComplete="current-password"
                required
              />
            </InputLayout>
            <div className="action-container">
              <Button
                disabled={fetchingLoginStatus === FETCH_STATUS.LOADING}
                className="login-button"
                type="primary"
                htmlType="submit">
                {tn('login')}
              </Button>
            </div>
            <div className="remember-container">
              <Checkbox>
                <TranslatedText text="remember_me" />
              </Checkbox>
              <Link to={RouteConstants.FORGOT_PASSWORD}>
                <TranslatedText text="forgot_password" />
              </Link>
            </div>
          </form>
        </Spin>
      </AuthenticationWrapper>
    </div>
  );
}

export function MarketingContent() {
  // TODO: Remove logic once marketing has url ready
  const tn = tNamespaced('Login');
  let MARKETING_CONTENT_URL = '';
  if (localStorage.getItem(LOCAL_STORAGE_KEY) !== '') {
    MARKETING_CONTENT_URL = localStorage.getItem(LOCAL_STORAGE_KEY) || '';
  }

  const isSyncariURL = (url: String) => {
    if (url.startsWith(SYNCARI_BASE_URL)) {
      return true;
    }
  };
  return (
    <div className="marketing-content">
      {isSyncariURL(MARKETING_CONTENT_URL) ? (
        <iframe className="marketing-iframe" title={tn('marketing_content')} src={MARKETING_CONTENT_URL} />
      ) : (
        <MarketingFallbackContent />
      )}
    </div>
  );
}

export default withI18n(Login, 'Login');
