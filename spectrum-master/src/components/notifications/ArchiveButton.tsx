//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tooltip } from 'antd';

import archiveSvg from 'assets/icons/archive.svg';
import InlineSvg from 'components/icons/InlineSvg';
import { tNamespaced } from 'utils/i18nUtil';
import { maxZIndex } from 'utils/StyleUtil';

import './ArchiveButton.less';

const tNotification = tNamespaced('Notification');

interface ArchiveButtonProps {
  archiving: boolean;
  onClick: () => void;
}

const ArchiveButton = ({ archiving, onClick }: ArchiveButtonProps) => {
  return (
    <Tooltip
      title={archiving ? tNotification('archiving') : tNotification('archive')}
      overlayStyle={{ zIndex: maxZIndex }}>
      <button className="notification-action notification-action-archive" onClick={onClick} disabled={archiving}>
        <InlineSvg src={archiveSvg} title={tNotification('archive')} />
      </button>
    </Tooltip>
  );
};

export default ArchiveButton;
