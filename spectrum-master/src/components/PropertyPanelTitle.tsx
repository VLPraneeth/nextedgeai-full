//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon, Tooltip } from 'antd';
import cx from 'classnames';
import * as React from 'react';

import HelpLink from './HelpLink';
import { HStack } from './layout';

import './PropertyPanelTitle.less';

interface PropertyPanelTitleProps {
  title: string;
  className?: string;
  helpPath?: string;
  icon?: string | React.ReactElement;
  iconType?: string;
  onClose?: () => void;
}

const PropertyPanelTitle = ({ className, icon, iconType, helpPath, onClose, title }: PropertyPanelTitleProps) => {
  const iconNode = iconType ? <Icon type={iconType} /> : icon;

  return (
    <div className={cx('property-panel-title-container', className)}>
      <HStack className="title-container" justify="space-between" align="center">
        <Tooltip title={title}>
          <div className="title">{title}</div>
        </Tooltip>
        <div className="icons-container">
          {iconNode && iconNode}
          {helpPath && <HelpLink helpPath={helpPath} />}
        </div>
      </HStack>
      {typeof onClose === 'function' && (
        <button aria-label="close panel" type="button" className="close-btn" onClick={onClose}>
          <Icon className="close-icon" type="close" />
        </button>
      )}
    </div>
  );
};

export default PropertyPanelTitle;
