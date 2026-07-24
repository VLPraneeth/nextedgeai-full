import { navigate, RouteComponentProps, Router } from '@reach/router';
import { Icon, Spin, Tag } from 'antd';
import { useEffect, useState } from 'react';

import { ReactComponent as EmailGroupIcon } from 'assets/icons/email-group.svg';
import { ReactComponent as WebhookIcon } from 'assets/icons/webhook.svg';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useGetErrorNotificationConfigsQuery } from 'store/error-notifications-v2/api';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { ErrorNotificationFormContextProvider } from './context/ErrorNotificationFormContext';
import { EmailsList } from './emails/EmailsList';
import './ErrorNotifications.scss';
import { ErrorNotificationForm } from './form/ErrorNotificationForm';
import { WebhookList } from './webhook/WebhookList';

const icon = {
  webhook: <Icon component={(props) => <WebhookIcon {...props} width={24} height={24} />} />,
  email: <Icon component={(props) => <EmailGroupIcon {...props} width={24} height={24} />} />,
};

const tn = tNamespaced('Settings.ErrorNotifications');

export default function ErrorNotifications({ location, uri }: RouteComponentProps) {
  const [selectedTab, setSelectedTab] = useState('');

  const { data: notificationConfigs, isLoading: notificationConfigsIsLoading } = useGetErrorNotificationConfigsQuery();

  const { userHasPermission } = useUserHasPermission();

  useEffect(() => {
    if (!location) {
      return;
    }

    if (location.pathname === RouteConstants.SETTINGS_NOTIFICATIONS) {
      if (userHasPermission(AllPermissions.READ_ERROR_NOTIFICATION_EMAIL)) {
        setSelectedTab('email');
        navigate(makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE, { type: 'email' }));
      } else if (userHasPermission(AllPermissions.READ_ERROR_NOTIFICATION_WEBHOOK)) {
        setSelectedTab('webhook');
        navigate(makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE, { type: 'webhook' }));
      }
    } else {
      const key = location.pathname.split('/').pop();
      if (key) {
        setSelectedTab(key);
      }
    }
  }, [location, userHasPermission]);

  const webhooks = notificationConfigs?.filter((notification) => notification.type === 'webhook');
  const emails = notificationConfigs?.filter((notification) => notification.type === 'email');

  const count = {
    email: emails?.length,
    webhook: webhooks?.length,
  };

  function handleTabChange(tabKey: string) {
    if (!uri) {
      return;
    }
    setSelectedTab(tabKey);
    navigate(`${uri}/${tabKey}`);
  }

  if (notificationConfigsIsLoading) {
    return <Spin />;
  }

  const isBaseUrl =
    location?.pathname === makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE, { type: 'webhook' }) ||
    location?.pathname === makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE, { type: 'email' });

  return (
    <ErrorNotificationFormContextProvider>
      <div className="error-notifications">
        {isBaseUrl && (
          <InlineTabs selectedTab={selectedTab} onChange={handleTabChange}>
            {userHasPermission(AllPermissions.READ_ERROR_NOTIFICATION_EMAIL) && (
              <InlineTab id="email">
                {icon['email']}
                <span className="error-notifications__tab-title">{tn('email')}</span>
                <Tag className="error-notifications__tags" color={selectedTab === 'email' ? 'blue' : ''}>
                  {count['email']}
                </Tag>
              </InlineTab>
            )}
            {userHasPermission(AllPermissions.READ_ERROR_NOTIFICATION_WEBHOOK) && (
              <InlineTab id="webhook">
                {icon['webhook']}
                <span className="error-notifications__tab-title">{tn('webhook')}</span>
                <Tag className="error-notifications__tags" color={selectedTab === 'webhook' ? 'blue' : ''}>
                  {count['webhook']}
                </Tag>
              </InlineTab>
            )}
          </InlineTabs>
        )}

        <Router>
          {userHasPermission(AllPermissions.READ_ERROR_NOTIFICATION_WEBHOOK) && (
            <>
              <WebhookList webhooks={webhooks} path="/" />
              <WebhookList webhooks={webhooks} path="/webhook" />
            </>
          )}
          {userHasPermission(AllPermissions.READ_ERROR_NOTIFICATION_EMAIL) && (
            <EmailsList path="/email" emails={emails} />
          )}
          {userHasPermission([
            AllPermissions.WRITE_ERROR_NOTIFICATION_EMAIL,
            AllPermissions.WRITE_ERROR_NOTIFICATION_WEBHOOK,
          ]) && (
            <>
              <ErrorNotificationForm path="/:type/add" />
              <ErrorNotificationForm path="/:type/:id/edit" />
            </>
          )}
        </Router>
      </div>
    </ErrorNotificationFormContextProvider>
  );
}
