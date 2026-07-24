import { SwitchProps as AntSwitchProps, default as BaseSwitch } from 'antd/lib/switch';
import cx from 'classnames';
import * as React from 'react';

import './Switch.less';

export interface SwitchProps extends AntSwitchProps {
  className?: string;
  label?: React.ReactNode;
  checkedLabel?: AntSwitchProps['checkedChildren'];
  uncheckedLabel?: AntSwitchProps['unCheckedChildren'];
  children?: React.ReactNode;
}

const Switch = ({ className, label, checkedLabel, uncheckedLabel, children, ...props }: SwitchProps) => {
  return (
    <div className={cx('switch-container', className)}>
      <label className="switch-label-container">
        <BaseSwitch
          className={cx('switch-control', { 'synri-switch-control-small': props.size === 'small' })}
          checkedChildren={checkedLabel}
          unCheckedChildren={uncheckedLabel}
          {...props}
        />
        <div className="switch-label-wrapper">
          {label && <div className="switch-label-text">{label}</div>}
          {children && <div className="switch-label-content">{children}</div>}
        </div>
      </label>
    </div>
  );
};

export default Switch;
