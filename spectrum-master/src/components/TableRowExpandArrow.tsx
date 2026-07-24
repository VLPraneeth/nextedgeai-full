//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';

import expandArrowSvg from 'assets/icons/expand-arrow.svg';
import InlineSvg from 'components/icons/InlineSvg';
import { Notification } from 'store/notifications/types';
import { tNamespaced } from 'utils/i18nUtil';

import './TableRowExpandArrow.less';

const tn = tNamespaced('NotificationList');

export interface TableRowExpandArrowProps {
  expanded: boolean;
  record: { id?: string };
  onExpand: (record: Notification, e: any) => void;
}

export default function TableRowExpandArrow({ expanded, record, onExpand }: TableRowExpandArrowProps) {
  return (
    <button
      aria-label={tn('expand_notification_aria_label', { notificationId: record?.id })}
      className={cx('table-row-expand-arrow', { 'is-expanded': expanded })}
      onClick={(e) => onExpand(record as Notification, e)}>
      <InlineSvg title="expand" src={expandArrowSvg} />
    </button>
  );
}
