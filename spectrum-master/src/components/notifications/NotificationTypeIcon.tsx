//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import notificationAnnouncementSvg from 'assets/icons/notification-announcement.svg';
import notificationErrorSvg from 'assets/icons/notification-error.svg';
import notificationInfoSvg from 'assets/icons/notification-info.svg';
import notificationWarnSvg from 'assets/icons/notification-warn.svg';
import InlineSvg from 'components/icons/InlineSvg';

import './NotificationTypeIcon.less';

export default function NotificationTypeIcon({ type, read }: any) {
  const typeToSvgSrc = {
    INFO: notificationInfoSvg,
    WARN: notificationWarnSvg,
    ERROR: notificationErrorSvg,
    ANNOUNCEMENT: notificationAnnouncementSvg,
  };

  return (
    <span className="notification-type-icon">
      {/* @ts-ignore */}
      <InlineSvg src={typeToSvgSrc[type]} />
      {!read && <span className="notification-unread-circle" />}
    </span>
  );
}
