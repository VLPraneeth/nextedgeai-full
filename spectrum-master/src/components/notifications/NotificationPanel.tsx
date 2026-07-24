//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Spin } from 'antd';
import moment from 'moment';
import { Fragment, useEffect, useMemo } from 'react';

import ChangeAwareLink from 'components/ChangeAwareLink';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { useLazyGetUnreadNotificationCountQuery, useLazyGetUnreadNotificationsQuery } from 'store/notifications/api';
import { NotificationGroups } from 'store/notifications/types';
import { tCommon, tNamespaced } from 'utils/i18nUtil';

import { NotificationCard } from './NotificationCard';
import NotificationErrorState from './NotificationErrorState';
import NotificationZeroState from './NotificationZeroState';

import './NotificationPanel.less';

const tn = tNamespaced('NotificationPanel');

export interface NotificationPanelProps {
  isOpen: boolean;
  handleClose: () => void;
}

export const NotificationPanel = ({ isOpen, handleClose }: NotificationPanelProps) => {
  const [
    getUnreadNotifications,
    { data: latestUnreadNotifications, isLoading, error: fetchError },
  ] = useLazyGetUnreadNotificationsQuery();
  const [getUnreadNotificationsCount] = useLazyGetUnreadNotificationCountQuery();

  useEffect(() => {
    if (isOpen) {
      getUnreadNotificationsCount();
      getUnreadNotifications();
    }
  }, [getUnreadNotifications, getUnreadNotificationsCount, isOpen]);

  // Split into groups
  const notificationGroups = useMemo(() => {
    const today = moment();
    const groups: NotificationGroups = { today: [], yesterday: [], this_week: [], last_week: [], older: [] };

    // Only showing the first 10 notifications in the panel for performance. The
    // user can see all notifications by tapping "All Notifications"
    latestUnreadNotifications?.slice(0, 10).forEach((notification) => {
      const creationDate = moment(notification.createdAt);
      const creationDayOfYear = creationDate.dayOfYear();
      const creationWeek = creationDate.week();

      const currentDayOfYear = today.dayOfYear();
      const currentWeek = today.week();

      // is today?
      if (creationDayOfYear === currentDayOfYear) {
        groups.today.push(notification);
      }
      // is yesterday?
      else if (creationDayOfYear === currentDayOfYear - 1) {
        groups.yesterday.push(notification);
      }
      // is This Week?
      else if (creationWeek === currentWeek) {
        groups.this_week.push(notification);
      }
      // is Last Week?
      else if (creationWeek === currentWeek - 1) {
        groups.last_week.push(notification);
      }
      // Older
      else {
        groups.older.push(notification);
      }
    });

    return groups;
  }, [latestUnreadNotifications]);

  return (
    <div className="notification-panel">
      <Spin spinning={isLoading} delay={300}>
        <ScrollableArea className="notification-panel__list" bottomOffset={52}>
          {fetchError ? (
            <NotificationErrorState />
          ) : latestUnreadNotifications && latestUnreadNotifications.length < 1 ? (
            <NotificationZeroState withLines />
          ) : (
            Object.entries(notificationGroups).map(([groupHeadingKey, notifications], index) => {
              // Ignore empty groups
              if (notifications.length === 0) {
                return null;
              }

              return (
                <Fragment key={groupHeadingKey + index}>
                  <h2 className="notification-panel__group-heading">{tCommon(groupHeadingKey)}</h2>
                  <ul>
                    {notifications.map((notification) => (
                      <NotificationCard
                        notification={notification}
                        handleClose={handleClose}
                        key={`dropdown-notification-${notification.id}`}
                      />
                    ))}
                  </ul>
                </Fragment>
              );
            })
          )}
        </ScrollableArea>
      </Spin>

      <div className="notification-panel__footer">
        <ChangeAwareLink className="notification-panel__all-link" onClick={handleClose} to="/notifications">
          {tn('all_notifications')}
        </ChangeAwareLink>
      </div>
    </div>
  );
};
