//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tooltip } from 'antd';
import { TooltipPlacement } from 'antd/lib/tooltip';
import cx from 'classnames';
import * as React from 'react';
import { useMemo } from 'react';

import EditIcon from 'assets/images/edit-icon.svg';
import Button, { ButtonType } from 'components/Button';
import SIcon from 'components/icons/SIcon';
import { AllPermissions } from 'utils/PermissionsConstants';

import './EmptyGraphPanel.less';

import Can from './Can';

export interface EmptyGraphPanelProps {
  className?: string;
  actionText?: string | React.ReactElement;
  actionButtonType?: ButtonType;
  onActionClick?: any;
  actionDisabled?: boolean;
  actionTooltip?: string;
  actionTooltipPlacement?: TooltipPlacement;
  actionPermission?: AllPermissions | AllPermissions[];
  icon?: any;
  panelIcon?: any;
  children?: React.ReactNode;
}

const EmptyGraphPanel = ({
  className,
  children,
  actionText,
  actionButtonType,
  onActionClick = () => {},
  actionDisabled = false,
  actionTooltip,
  actionTooltipPlacement,
  actionPermission,
  icon,
  panelIcon,
}: EmptyGraphPanelProps) => {
  if (!panelIcon) {
    let displayedIcon = icon;
    if (!displayedIcon) {
      displayedIcon = EditIcon;
    }
    if (displayedIcon) {
      panelIcon = <SIcon src={displayedIcon} size={SIcon.SIZE.L_LARGE} />;
    }
  }

  let actionButton = useMemo(() => {
    if (actionText && onActionClick) {
      let actionButton = (
        <div className="action-container">
          {actionPermission ? (
            <Can permission={actionPermission}>
              <Button type={actionButtonType} disabled={actionDisabled} onClick={onActionClick}>
                {actionText}
              </Button>
            </Can>
          ) : (
            <Button type={actionButtonType} disabled={actionDisabled} onClick={onActionClick}>
              {actionText}
            </Button>
          )}
        </div>
      );

      if (actionTooltip) {
        actionButton = (
          <Tooltip title={actionTooltip} placement={actionTooltipPlacement}>
            {actionButton}
          </Tooltip>
        );
      }

      return actionButton;
    }
    return null;
  }, [
    actionButtonType,
    actionDisabled,
    actionPermission,
    actionText,
    actionTooltip,
    actionTooltipPlacement,
    onActionClick,
  ]);

  return (
    <div className={cx('synri-empty-graph-panel', className)}>
      <div className="icon-container" key="empty-graph-icon">
        {panelIcon}
      </div>
      <div className="text-container" key="empty-text-container">
        {children}
      </div>
      {actionButton}
    </div>
  );
};

export default EmptyGraphPanel;
