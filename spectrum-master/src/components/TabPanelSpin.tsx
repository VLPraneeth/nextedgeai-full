//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Spin, { SpinProps } from 'antd/lib/spin';
import cx from 'classnames';
import * as React from 'react';

import './TabPanelSpin.less';

export interface TabPanelSpinProps extends SpinProps {
  spinning?: boolean;
  tip?: string;
  className?: string;
  delay?: number;
  children?: React.ReactNode;
}

const TabPanelSpin = ({
  spinning = false,
  delay = 10,
  tip = '',
  className,
  size = 'large',
  children,
}: TabPanelSpinProps) => {
  return (
    <>
      {spinning ? (
        <div className={cx('synri-tab-panel-spin', className)}>
          <Spin delay={delay} spinning={spinning} size={size} tip={tip} />
        </div>
      ) : (
        children
      )}
    </>
  );
};

export default TabPanelSpin;
