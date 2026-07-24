//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import ASteps, { StepsProps as AStepsProps } from 'antd/lib/steps';
import cx from 'classnames';
import * as React from 'react';

import './Steps.less';

export const { Step } = ASteps;

export interface StepsProps extends AStepsProps {
  children: React.ReactNode;
}

export const Steps = ({ children, className, ...rest }: StepsProps) => {
  return (
    <ASteps className={cx('synri-steps', className)} {...rest}>
      {children}
    </ASteps>
  );
};

export default Steps;
