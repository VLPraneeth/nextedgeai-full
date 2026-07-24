//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import ArchiveButton from 'components/notifications/ArchiveButton';
import MarkAsButton from 'components/notifications/MarkAsButton';
import {
  useArchiveNotificationsMutation,
  useMarkNotificationsReadMutation,
  useMarkNotificationsUnreadMutation,
} from 'store/notifications/api';
import { Notification } from 'store/notifications/types';

import './NotificationActionsRenderer.less';

export interface NotificationActionsRendererProps {
  record: Notification;
}

const NotificationActionsRenderer = ({ record: { id, read } }: NotificationActionsRendererProps) => {
  const [markAsReadMutation, { isLoading: isMarkingAsRead }] = useMarkNotificationsReadMutation();
  const [markAsUnreadMutation, { isLoading: isMarkingAsUnread }] = useMarkNotificationsUnreadMutation();
  const [archiveMutation, { isLoading: isArchiving }] = useArchiveNotificationsMutation();
  const isMarking = isMarkingAsRead || isMarkingAsUnread;

  return (
    <>
      <ArchiveButton archiving={isArchiving} onClick={() => archiveMutation([id])} />
      <MarkAsButton
        marking={isMarking}
        read={read}
        onMarkAsRead={() => markAsReadMutation([id])}
        onMarkAsUnread={() => markAsUnreadMutation([id])}
      />
    </>
  );
};

export default NotificationActionsRenderer;
