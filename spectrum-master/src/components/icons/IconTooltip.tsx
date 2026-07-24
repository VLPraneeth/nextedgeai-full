//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Tooltip, { TooltipPlacement } from 'antd/lib/tooltip';

import InlineSvg from 'components/icons/InlineSvg';

interface IconTooltipProps {
  iconTitle?: string;
  iconPath?: string;
  iconClassname?: string;
  tooltipTitle?: string;
  tooltipPlacement?: TooltipPlacement;
  isActive?: boolean;
  [k: string]: any;
}
const IconTooltip = ({
  children,
  iconPath,
  iconTitle = '',
  iconClassname,
  tooltipTitle,
  tooltipPlacement = 'bottom',
  isActive,
  ...props
}: IconTooltipProps) => {
  return (
    <Tooltip title={tooltipTitle} placement={tooltipPlacement}>
      <div {...props}>
        {iconPath ? <InlineSvg src={iconPath} title={iconTitle} className={iconClassname} /> : children}
      </div>
    </Tooltip>
  );
};

export default IconTooltip;
