import { Tooltip as AntTooltip } from 'antd';
import { TooltipProps as AntTooltipProps } from 'antd/lib/tooltip';

const tooltipDelayInSeconds = 1;

const Tooltip = ({ children, ...rest }: AntTooltipProps) => (
  <AntTooltip mouseEnterDelay={tooltipDelayInSeconds} {...rest}>
    {children}
  </AntTooltip>
);

export default Tooltip;
