import { Link, navigate, RouteComponentProps } from '@reach/router';
import { Button, Input, message } from 'antd';
import { FormEvent, useState } from 'react';

import InputLayout from 'components/layout/InputLayout';
import { TranslatedText } from 'components/typography';
import AuthenticationWrapper from 'pages/authentication/AuthenticationWrapper';
import { MarketingContent } from 'pages/authentication/Login';
import { post } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import './PasswordPage.scss';

const tn = tNamespaced('InsightsStudio');

export default function PasswordPage({
  dashboardId,
  email: emailFromUrl,
}: RouteComponentProps & { dashboardId: string; email: string }) {
  const [password, setPassword] = useState('');
  const email = atob(emailFromUrl);
  function handleSubmit(evt: FormEvent<HTMLFormElement>) {
    evt.preventDefault();
    const formBody = new FormData();
    formBody.set('username', email);
    formBody.set('password', password);
    post(DataUrlConstants.LOGIN, formBody)
      .then(() => {
        navigate(
          makeUrl(RouteConstants.INSIGHTS_STUDIO_SHARED_DASHBOARD, {
            dashboardId,
            email: emailFromUrl,
          })
        );
      })
      .catch((error) => {
        message.error(error?.response?.data?.message);
      });
  }

  return (
    <div className="password-page">
      <div className="password-page__marketing-content">
        <MarketingContent />
      </div>
      <AuthenticationWrapper
        className="password-page__wrapper"
        footer={<TranslatedText namespace="Login" beDangerous text="demo" />}>
        <div className="password-page__text">{tn('InsightsSharing.password_page_text', { user: email })}</div>
        <form onSubmit={handleSubmit}>
          <InputLayout className="input-password" label={tc('enter_password')}>
            <Input.Password
              visibilityToggle={false}
              name="password"
              className="password-input"
              placeholder={tc('enter_password')}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              value={password}
              required
            />
          </InputLayout>

          <Button disabled={!password.length} className="password-page__button" type="primary" htmlType="submit">
            {tc('submit')}
          </Button>

          <div className="password-page__forgot-password">
            <Link to={RouteConstants.FORGOT_PASSWORD}>
              <TranslatedText namespace="Login" text="forgot_password" />
            </Link>
          </div>
        </form>
      </AuthenticationWrapper>
    </div>
  );
}
