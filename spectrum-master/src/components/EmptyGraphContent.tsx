//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button } from 'antd';
import cx from 'classnames';
import * as React from 'react';

import { AllPermissions } from 'utils/PermissionsConstants';

import Can from './Can';

import './EmptyGraphContent.less';

export interface EmptyGraphContentProps {
  icon: React.ReactElement;
  className?: string;
  actionDisabled?: boolean;
  actionText?: string;
  actionPermission?: AllPermissions | AllPermissions[];
  onActionClick?: () => void;
  children?: React.ReactNode;
}

const EmptyGraphContent = ({
  className,
  icon,
  children,
  actionDisabled = false,
  actionText = '',
  actionPermission,
  onActionClick = () => {},
}: EmptyGraphContentProps) => {
  return (
    <div className={cx(className, 'synri-empty-graph-content')}>
      <div className="icon-container" key="empty-graph-icon">
        {icon}
      </div>
      <div className="text-container" key="empty-text-container">
        {children}
      </div>
      {actionText && onActionClick && (
        <div className="action-container">
          {actionPermission ? (
            <Can permission={actionPermission}>
              <Button type="primary" onClick={onActionClick} disabled={actionDisabled}>
                {actionText}
              </Button>
            </Can>
          ) : (
            <Button type="primary" onClick={onActionClick} disabled={actionDisabled}>
              {actionText}
            </Button>
          )}
        </div>
      )}
    </div>
  );
};

export default EmptyGraphContent;
