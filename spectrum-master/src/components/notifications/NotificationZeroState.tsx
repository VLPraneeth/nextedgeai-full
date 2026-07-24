//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import cx from 'classnames';

import notificationCircleBellSvg from 'assets/icons/notification-circle-bell.svg';
import InlineSvg from 'components/icons/InlineSvg';
import { tNamespaced } from 'utils/i18nUtil';

import './NotificationZeroState.less';

const tn = tNamespaced('Notification');

export interface NotificationZeroStateProps {
  withLines?: boolean;
}

const NotificationZeroState = ({ withLines }: NotificationZeroStateProps) => {
  return (
    <div className="notification-zero-state-container">
      <div
        className={cx('notification-bell-container', {
          'with-lines': withLines,
        })}>
        <InlineSvg title={tn('notification_bell')} src={notificationCircleBellSvg} />
      </div>
      <div className="notification-zero-state-title">{tn('no_notifications_title')}</div>
      <div className="notification-zero-state-subtitle">{tn('no_notifications_subtitle')}</div>
    </div>
  );
};

export default NotificationZeroState;
