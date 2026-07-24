//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import * as React from 'react';

import SyncarooErrorImage from 'assets/images/syncari-404.png';

import './Err.less';

interface ErrProps {
  /**
   * Additional classname added to the error contianer
   */
  className?: string;

  children?: React.ReactNode;
}

const Err = ({ children, className }: ErrProps) => {
  return (
    <div className={cx('synri-error-container', className)}>
      <div className="synri-error-message">
        <img className="synri-error-image" alt="" src={SyncarooErrorImage} />
        {children}
      </div>
    </div>
  );
};

export default Err;
