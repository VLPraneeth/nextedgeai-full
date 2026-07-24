import Tooltip, { TooltipProps } from 'antd/lib/tooltip';
import cx from 'classnames';
import * as React from 'react';

import './ListItem.less';

export enum ListItemStatus {
  disabled = 'disabled',
}

export interface ListItemProps {
  title: string;
  titleTooltip?: TooltipProps['title'];
  tags?: React.ReactNode;
  description?: string | React.ReactNode;
  descriptionTooltip?: TooltipProps['title'];
  onClick?: () => void;
  className?: string;
  icon?: React.ReactNode;
  rightContent?: React.ReactNode;
  status?: ListItemStatus;
}

const ListItem = ({
  title,
  titleTooltip,
  tags,
  description,
  descriptionTooltip,
  onClick,
  className,
  icon,
  rightContent,
  status,
}: ListItemProps) => {
  return (
    <div
      className={cx(
        'synri-list-item',
        className,
        status && `synri-list-item-${status}`,
        onClick && 'synri-list-item-clickable'
      )}>
      <div className="synri-list-item-content" role="button" onClick={onClick}>
        {icon && <div className="synri-list-item-logo">{icon}</div>}
        <div className="synri-list-item-text-container">
          <Tooltip title={titleTooltip} mouseEnterDelay={1}>
            <div className="synri-list-item-title">{title}</div>
          </Tooltip>
          <Tooltip title={descriptionTooltip} mouseEnterDelay={1}>
            <div className="synri-list-item-description-container">
              {tags}
              <div className="synri-list-item-description">{description}</div>
            </div>
          </Tooltip>
        </div>
      </div>
      {rightContent}
    </div>
  );
};

export default ListItem;
