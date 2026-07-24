import cx from 'classnames';
import * as React from 'react';

import './StatusBadge.less';

export enum StatusBadgeSize {
  SMALL = 'small',
  DEFAULT = 'default',
}

const isStatusBadgeSize = (variableToCheck: any): variableToCheck is StatusBadgeSize => {
  return Object.values(StatusBadgeSize).includes(variableToCheck);
};

export enum StatusBadgeType {
  DEFAULT = 'default',
  INFO = 'info',
  SUCCESS = 'success',
  WARNING = 'warning',
  DANGER = 'danger',
  SPECIAL = 'special',
  ERROR = 'error',
}

const isStatusBadgeType = (variableToCheck: any): variableToCheck is StatusBadgeType => {
  return Object.values(StatusBadgeType).includes(variableToCheck);
};

export interface StatusBadgeProps {
  /** optional classname for this badge */
  className?: string;
  /** size of badge */
  size?: StatusBadgeSize;
  /** what kind of badge to show: info/warning/danger/etc */
  type?: StatusBadgeType;

  children?: React.ReactNode;
}

const StatusBadge = ({
  children,
  className,
  size = StatusBadgeSize.DEFAULT,
  type = StatusBadgeType.DEFAULT,
}: StatusBadgeProps) => {
  return (
    <div
      className={cx(
        'synri-badge',
        size !== StatusBadgeSize.DEFAULT && isStatusBadgeSize(size) && `synri-status-badge-${size}`,
        type !== StatusBadgeType.DEFAULT && isStatusBadgeType(type) && `synri-status-badge-${type}`,
        className
      )}>
      {children}
    </div>
  );
};

export default StatusBadge;
