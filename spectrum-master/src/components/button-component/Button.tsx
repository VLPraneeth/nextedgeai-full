//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import AntButton, { ButtonProps as AntButtonProps, ButtonType } from 'antd/lib/button';
import cx from 'classnames';
import * as React from 'react';

import './Button.less';

const ButtonTypes = ['default', 'primary', 'ghost', 'dashed', 'danger', 'link'] as const;

interface ButtonProps extends Omit<AntButtonProps, 'type'> {
  /**
   * The CSS class name that will be added to the container.
   */
  className?: string;
  /**
   * The children of this component.
   */
  children?: React.ReactNode;
  /**
   * The type of button that will be rendered.
   * @default 'default'
   */
  type?: typeof ButtonTypes[number];
}

const Button = ({ children, className, type = 'default', ...props }: ButtonProps) => {
  return (
    <AntButton className={cx('synri-button', className)} type={type} {...props}>
      {children}
    </AntButton>
  );
};

interface IconButtonProps extends Omit<ButtonProps, 'icon'> {
  icon?: React.FC<React.SVGProps<SVGSVGElement>>;
  iconProps?: React.SVGProps<SVGSVGElement>;
}

const IconButton = ({ icon: Icon, iconProps, className, children, size, ...props }: IconButtonProps) => {
  return (
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
      {...props}>
      <div className="icon-wrapper">
        {Icon && <Icon {...iconProps} />}
        {children}
      </div>
    </AntButton>
  );
};

export default Button;
export { ButtonTypes, IconButton };

export type { ButtonProps, ButtonType, IconButtonProps };
