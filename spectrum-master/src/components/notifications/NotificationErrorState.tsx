import notificationCircleErrorSvg from 'assets/icons/notification-error.svg';
import InlineSvg from 'components/icons/InlineSvg';
import { tNamespaced } from 'utils/i18nUtil';

import './NotificationErrorState.less';

const t = tNamespaced('Notification');

function NotificationsErrorState() {
  return (
    <div className="notification-zero-state-container">
      <div className="notification-bell-container with-lines">
        {/* @ts-ignore */}
        <InlineSvg className="error-icon" src={notificationCircleErrorSvg} />
      </div>
      <div className="notification-zero-state-title">{t('error_loading_notifications_title')}</div>
      <div className="notification-zero-state-subtitle">{t('error_loading_notifications_description')}</div>
    </div>
  );
}

export default NotificationsErrorState;
