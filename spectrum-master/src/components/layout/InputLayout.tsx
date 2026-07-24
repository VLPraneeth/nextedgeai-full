//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import classNames from 'classnames';
import { Component } from 'react';

import './InputLayout.less';

class InputLayout extends Component<{ className?: string; label?: string; children: any }> {
  render() {
    const { className, children, label } = this.props;
    const cls = classNames('input-layout-container', className);
    return (
      <div className={cls}>
        <div className="label-container">{label}</div>
        {children}
      </div>
    );
  }
}

export default InputLayout;
