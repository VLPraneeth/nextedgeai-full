//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import * as React from 'react';

import './style';

export interface CenterProps {
  /**
   * CSS class name added to the container
   */
  className?: string;
  /**
   * Children of the component
   */
  children?: React.ReactNode;
}

export const Center = ({ children, className }: CenterProps) => (
  <div className={cx('synri-center', className)}>{children}</div>
);

export default Center;
