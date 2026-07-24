//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { default as ACollapse, CollapseProps as ACollapseProps } from 'antd/lib/collapse/Collapse';
import Icon from 'antd/lib/icon';
import cx from 'classnames';
import * as React from 'react';

import './Collapse.less';

const { Panel } = ACollapse;

export interface CollapseProps extends ACollapseProps {
  className?: string;
  children?: React.ReactNode;
}

const Collapse = ({ className, children, ...rest }: CollapseProps) => {
  return (
    <ACollapse
      className={cx('synri-collapse', className)}
      expandIcon={({ isActive }) => <Icon type="caret-right" rotate={isActive ? 90 : 0} />}
      {...rest}>
      {children}
    </ACollapse>
  );
};

export default Collapse;

export { Panel };
