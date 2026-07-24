import { Tooltip as AntTooltip } from 'antd';
import { TooltipProps as AntTooltipProps } from 'antd/lib/tooltip';

import AppConstants from 'utils/AppConstants';
import { tc } from 'utils/i18nUtil';

const PermissionErrorTooltip = ({ children, title, ...rest }: AntTooltipProps) => (
  <AntTooltip mouseEnterDelay={AppConstants.TOOLTIP_DELAY_SECONDS} title={title ?? tc('permission_error')} {...rest}>
    {children}
  </AntTooltip>
);

export default PermissionErrorTooltip;
