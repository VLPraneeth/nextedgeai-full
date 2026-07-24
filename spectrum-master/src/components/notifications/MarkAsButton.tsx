//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tooltip } from 'antd';

import markAsReadSvg from 'assets/icons/mark-as-read.svg';
import markAsUnreadSvg from 'assets/icons/mark-as-unread.svg';
import InlineSvg from 'components/icons/InlineSvg';
import { tNamespaced } from 'utils/i18nUtil';
import { maxZIndex } from 'utils/StyleUtil';

import './MarkAsButton.less';

const tn = tNamespaced('Notification');

export interface MarkAsButtonProps {
  read: boolean;
  marking: boolean;
  onMarkAsRead: () => void;
  onMarkAsUnread: () => void;
}

const MarkAsButton = ({ read, marking, onMarkAsRead, onMarkAsUnread }: MarkAsButtonProps) => {
  return (
    <Tooltip
      title={
        marking
          ? read
            ? tn('marking_as_unread')
            : tn('marking_as_read')
          : read
          ? tn('mark_as_unread')
          : tn('mark_as_read')
      }
      overlayStyle={{ zIndex: maxZIndex }}>
      <button
        className="notification-action notification-action-mark-as"
        onClick={read ? onMarkAsUnread : onMarkAsRead}
        disabled={marking}>
        <InlineSvg
          src={read ? markAsUnreadSvg : markAsReadSvg}
          title={read ? tn('mark_as_unread') : tn('mark_as_read')}
        />
      </button>
    </Tooltip>
  );
};

export default MarkAsButton;
