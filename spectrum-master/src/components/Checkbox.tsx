import AntCheckbox, {
  CheckboxProps as AntCheckboxProps,
  CheckboxChangeEvent as CheckboxChangeEvent_,
} from 'antd/lib/checkbox';
import cx from 'classnames';

import './Checkbox.less';

export type CheckboxChangeEvent = CheckboxChangeEvent_;

export interface CheckboxProps extends AntCheckboxProps {
  className?: string;
}

const Checkbox = ({ className, children, ...props }: CheckboxProps) => (
  <div className={cx('synri-checkbox', className)}>
    <AntCheckbox {...props}>{children}</AntCheckbox>
  </div>
);

export default Checkbox;
