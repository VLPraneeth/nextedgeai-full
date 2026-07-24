// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';

import { tNamespaced } from 'utils/i18nUtil';

import './NotificationReadRenderer.less';

const tNotification = tNamespaced('Notification');

const ReadLabel = ({ read }) => (
  <div
    className={cx('notification-read-label', {
      'is-read': read,
      'is-unread': !read,
    })}>
    {read ? tNotification('read') : tNotification('unread')}
  </div>
);

export default function NotificationReadRenderer({ text: read }) {
  return (
    <div>
      <ReadLabel read={read} />
    </div>
  );
}
