//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon } from 'antd';
import Truncate from 'react-truncate';

import Button from 'components/Button';
import ChangeAwareLink from 'components/ChangeAwareLink';
import useUserLocalMoment from 'hooks/moment';
import {
  useArchiveNotificationsMutation,
  useMarkNotificationsReadMutation,
  useMarkNotificationsUnreadMutation,
} from 'store/notifications/api';
import { Notification } from 'store/notifications/types';
import { t } from 'utils/i18nUtil';

import ArchiveButton from './ArchiveButton';
import MarkAsButton from './MarkAsButton';
import NotificationTypeIcon from './NotificationTypeIcon';

import './NotificationCard.less';

interface NotificationCardProps {
  notification: Notification;
  handleClose: () => void;
}

export const NotificationCard = ({ notification, handleClose }: NotificationCardProps) => {
  const { id, subject, body, read, type, createdAt } = notification;

  const userMoment = useUserLocalMoment();

  const [markNotificationsRead, { isLoading: isMarkingRead }] = useMarkNotificationsReadMutation();
  const [markNotificationsUnread, { isLoading: isMarkingUnread }] = useMarkNotificationsUnreadMutation();
  const [archiveNotifications, { isLoading: isArchiving }] = useArchiveNotificationsMutation();

  return (
    <div className="notification-card">
      <div className="notification-card__icon">
        <NotificationTypeIcon type={type} read={read} />
      </div>
      <div className="notification-card__content">
        <div className="notification-card__subject">
          <div className="notification-card__subject-text">{subject}</div>
          <div className="notification-card__actions">
            <MarkAsButton
              marking={isMarkingRead || isMarkingUnread}
              read={read}
              onMarkAsRead={() => markNotificationsRead([id])}
              onMarkAsUnread={() => markNotificationsUnread([id])}
            />
            <ArchiveButton archiving={isArchiving} onClick={() => archiveNotifications([id])} />
          </div>
        </div>
        <div className="notification-card__body">
          <Truncate lines={3}>{body}</Truncate>
        </div>
        <ChangeAwareLink
          className="notification-card__more-button"
          to="/notifications"
          state={{ notificationId: id }}
          onClick={() => {
            markNotificationsRead([id]);
            handleClose();
          }}>
          <Button size="small">
            {t('NotificationPanel.more_info')}
            <Icon type="arrow-right" />
          </Button>{' '}
        </ChangeAwareLink>
        <div className="notification-card__time">{userMoment(createdAt).fromNow()}</div>
      </div>
    </div>
  );
};
