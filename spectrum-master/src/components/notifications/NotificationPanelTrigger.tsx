import { Badge, Icon } from 'antd';
import cx from 'classnames';

import useRerenderAfterDelay from 'hooks/useRerenderAfterDelay';
import { ONE_MINUTE, TEN_SECONDS } from 'store/api/constants';
import { notificationEndpoints, useGetUnreadNotificationCountQuery } from 'store/notifications/api';
import { t } from 'utils/i18nUtil';
import { UserflowTags } from 'utils/UserflowTags';

import './NotificationPanelTrigger.less';

interface NotificationPanelTriggerProps {
  onClick: () => void;
  isOpen: boolean;
}

export const NotificationPanelTrigger = ({ onClick, isOpen }: NotificationPanelTriggerProps) => {
  // Delay fetching the unread notification count for 10 seconds to let the rest
  // of the app to load first
  const readyToFetch = useRerenderAfterDelay(TEN_SECONDS);

  useGetUnreadNotificationCountQuery(undefined, {
    pollingInterval: ONE_MINUTE,
    skip: !readyToFetch,
  });
  // rtk-query won't return data when `skip: true`, even if it's been fetched by
  // another query already. We use `useQueryState` here in order to read the
  // cached data to get around this (like if the user is on the notifications
  // page).
  const queryState = notificationEndpoints.getUnreadNotificationCount.useQueryState();
  const unreadCount = queryState.data;

  return (
    <button
      data-userflow-tag={UserflowTags.Header.Notifications}
      className={cx('header-menu-item notification-panel-trigger', {
        'is-open': isOpen,
      })}
      aria-label={t('NotificationPanel.toggle_notifications_panel_label')}
      onClick={onClick}>
      <Badge count={unreadCount}>
        <Icon type="bell" className="header-icon" theme="filled" />
      </Badge>
    </button>
  );
};
