import Checkbox, { CheckboxChangeEvent, CheckboxProps } from 'antd/lib/checkbox';
import { useCallback } from 'react';

import { InputProxyOnChangeEvent } from './types';

/* don't allow defaultValue - this must be a controlled component */
export interface CheckboxProxyProps extends Omit<CheckboxProps, 'defaultValue' | 'onChange'> {
  onChange: InputProxyOnChangeEvent<boolean, CheckboxChangeEvent>;
}

const CheckboxProxy = ({ id, name, onChange, checked, value, ...props }: CheckboxProxyProps) => {
  const handleChange = useCallback(
    (evt: CheckboxChangeEvent) => {
      onChange(evt.target.checked, name, id, evt);
    },
    [id, name, onChange]
  );

  // coalesce value/checked
  const isChecked = typeof value === 'boolean' ? value : checked;

  return <Checkbox id={id} name={name} onChange={handleChange} checked={isChecked} {...props} />;
};

export default CheckboxProxy;
