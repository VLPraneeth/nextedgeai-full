// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import NotificationTypeIcon from 'components/notifications/NotificationTypeIcon';

import './NotificationSubjectRenderer.less';

export default function NotificationSubjectRenderer({ text, record: { type, read } }) {
  return (
    <div className="notification-subject-renderer">
      <NotificationTypeIcon type={type} read={read} />
      {text}
    </div>
  );
}
