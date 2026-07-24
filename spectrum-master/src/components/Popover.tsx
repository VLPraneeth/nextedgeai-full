import AntPopover, { PopoverProps as AntPopoverProps } from 'antd/lib/popover';
import cx from 'classnames';

import './Popover.less';

export interface PopoverProps extends AntPopoverProps {}

const Popover = ({ overlayClassName, ...props }: PopoverProps) => (
  <AntPopover overlayClassName={cx('syncari-popover-overlay', overlayClassName)} {...props} />
);

export default Popover;
