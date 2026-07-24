import { Icon, Tooltip } from 'antd';

import Spinner from 'components/Spinner';
import { useGetUnreadNotificationCountQuery, useMarkAllNotificationReadMutation } from 'store/notifications/api';
import { tCommon, tNamespaced } from 'utils/i18nUtil';

import './NotificationPanelControls.less';

const tn = tNamespaced('NotificationPanel');

interface NotificationPanelControlsProps {
  onClose: () => void;
}

export const NotificationPanelControls = ({ onClose }: NotificationPanelControlsProps) => {
  const [markAllAsReadMutation, { isLoading: isMarkingAllRead }] = useMarkAllNotificationReadMutation();
  const { data: unreadCount } = useGetUnreadNotificationCountQuery();

  const markAllAsReadDisabled = !unreadCount || isMarkingAllRead;

  let tooltipText = tn('mark_all_as_read');
  if (isMarkingAllRead) {
    tooltipText = tn('marking_all_as_read');
  } else if (!unreadCount) {
    tooltipText = tn('all_notifications_are_read');
  }

  return (
    <div className="notification-panel-controls">
      {/* Mark all as read icon */}
      <Tooltip placement="bottom" title={tooltipText}>
        <div className="notification-panel-controls__mark-unread">
          {isMarkingAllRead ? (
            <Spinner spinning />
          ) : (
            <button
              disabled={markAllAsReadDisabled}
              className="notification-panel-controls__control"
              onClick={() => markAllAsReadMutation()}>
              <Icon type="check" className="header-icon" />
            </button>
          )}
        </div>
      </Tooltip>

      {/* Close icon*/}
      <Tooltip placement="bottom" title={tCommon('close')}>
        <button className="notification-panel-controls__control" onClick={onClose}>
          <Icon type="close" className="header-icon" />
        </button>
      </Tooltip>
    </div>
  );
};
