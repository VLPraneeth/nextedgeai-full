//
// Copyright (c) 2019-Present Syncari All rights reserved.
//
import AntButton, { ButtonProps as AntButtonProps, ButtonType } from 'antd/lib/button';
import cx from 'classnames';
import * as React from 'react';

import './button-component/Button.less';
import Tooltip from './tooltip/Tooltip';

const ButtonTypes = ['default', 'primary', 'ghost', 'dashed', 'danger', 'link'];

export type ButtonProps = AntButtonProps;

const Button = ({ children, className, ...props }: ButtonProps) => {
  return (
    <AntButton className={cx('synri-button', className)} {...props}>
      {children}
    </AntButton>
  );
};

export interface IconButtonProps extends Omit<ButtonProps, 'icon'> {
  icon?: React.FC<React.SVGProps<SVGSVGElement>>;
  iconProps?: React.SVGProps<SVGSVGElement>;
}

const IconButton = ({
  icon: Icon,
  iconProps,
  className,
  children,
  size,
  title,
  onClick,
  ...props
}: IconButtonProps) => {
  const [tooltipVisible, setTooltipVisible] = React.useState(false);

  const handleClick = (e: React.MouseEvent<HTMLElement>) => {
    // Hide tooltip immediately when clicked
    setTooltipVisible(false);
    if (onClick) {
      onClick(e);
    }
  };

  const button = (
    <AntButton
      className={cx(
        'synri-button',
        'synri-icon-button',
        {
          small: size === 'small',
        },
        className
      )}
      size={size}
      onClick={handleClick}
      {...props}>
      <div className="icon-wrapper">
        {Icon && <Icon {...iconProps} />}
        {children}
      </div>
    </AntButton>
  );

  // Use Ant Design Tooltip for instant tooltip display instead of native title attribute
  if (title) {
    return (
      <Tooltip title={title} mouseEnterDelay={0.1} visible={tooltipVisible} onVisibleChange={setTooltipVisible}>
        {button}
      </Tooltip>
    );
  }

  return button;
};

export default Button;
export { ButtonTypes, IconButton };

export type { ButtonType };
