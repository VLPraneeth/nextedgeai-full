//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import * as React from 'react';

import './CenterLayout.less';

const CenterLayout = ({ className, children }: { className?: string; children?: React.ReactNode }) => {
  return <div className={cx('center-layout-container', className)}>{children}</div>;
};

export default CenterLayout;
