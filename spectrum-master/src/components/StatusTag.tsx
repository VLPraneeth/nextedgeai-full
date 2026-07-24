//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import cx from 'classnames';

import { ReactComponent as InfoIcon } from 'assets/icons/info-icon-solid.svg';

import Tooltip from './tooltip/Tooltip';
import { Text } from './typography';

import './StatusTag.less';

export type StatusTagColors = 'green' | 'blue' | 'orange' | 'gray' | 'red' | 'purple' | 'alert';

export interface StatusTagProps {
  text: string;
  color: StatusTagColors;
  tooltipText?: string;
  large?: boolean;
}

const StatusTag = ({ text, color, tooltipText, large = false }: StatusTagProps) => {
  return (
    <Tooltip title={tooltipText} mouseEnterDelay={0.3} placement="bottom">
      <div className={cx('synri-status-tag', color, large && 'large')}>
        {tooltipText ? (
          <>
            <InfoIcon />
            <Text className="synri-info-text">{text}</Text>
          </>
        ) : (
          text
        )}
      </div>
    </Tooltip>
  );
};

export default StatusTag;
