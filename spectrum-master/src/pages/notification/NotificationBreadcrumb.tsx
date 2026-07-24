import { RouteComponentProps } from '@reach/router';
import { startCase } from 'lodash';
import { useEffect, useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { useGetErrorNotificationConfigsQuery } from 'store/error-notifications-v2/api';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

export interface NotificationBreadcrumbProps extends RouteComponentProps {
  type?: string;
  id?: string;
  action?: string;
}

export const NotificationBreadcrumb = withI18n(({ type, id, action }: NotificationBreadcrumbProps) => {
  const { data: errorNotifications } = useGetErrorNotificationConfigsQuery();
  const { t, tn } = useI18nContext();
  const { setUrlName } = useBreadcrumb();

  useEffect(() => {
    setUrlName(RouteConstants.SETTINGS_NOTIFICATIONS, `${t('SideNavigationMenu.settings')}・${tn('notifications')}`);
    setUrlName(makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE, { type: 'webhook' }), 'Webhook');
    setUrlName(makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE, { type: 'email' }), 'Email');
  }, [setUrlName, t, tn]);

  const [notificationName, url] = useMemo(() => {
    const notification = errorNotifications?.find((notification) => notification.id === id);
    const name = notification?.name || id;
    let baseUrl = RouteConstants.SETTINGS_NOTIFICATIONS;
    switch (action?.toLowerCase()) {
      case 'add':
        baseUrl = RouteConstants.SETTINGS_NOTIFICATIONS_TYPE_ADD;
        break;
      case 'edit':
        baseUrl = RouteConstants.SETTINGS_NOTIFICATIONS_TYPE_EDIT;
        break;
      default:
        baseUrl = RouteConstants.SETTINGS_NOTIFICATIONS_TYPE;
    }
    const url = makeUrl(baseUrl, { type, id });
    if (notification && notification.name) {
      setUrlName(url, notification.name);
    }
    return [name, url];
  }, [action, errorNotifications, id, setUrlName, type]);

  return (
    <>
      <BreadcrumbLink to={RouteConstants.SETTINGS}>{t('SideNavigationMenu.settings')}</BreadcrumbLink>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={RouteConstants.SETTINGS_NOTIFICATIONS}>{tn('notifications')}</BreadcrumbLink>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE, { type })}>
        {startCase(type)}
      </BreadcrumbLink>
      {id && (
        <>
          <BreadcrumbSeparator />
          <BreadcrumbLink to={url}>{notificationName}</BreadcrumbLink>
        </>
      )}
    </>
  );
}, 'Profile');
