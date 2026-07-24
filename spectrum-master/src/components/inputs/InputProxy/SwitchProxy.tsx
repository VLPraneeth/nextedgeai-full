import Switch, { SwitchProps } from 'antd/lib/switch';
import { useCallback } from 'react';

import { InputRenderType } from '../types';
import { InputProxyOnChangeEvent } from './types';

export interface SwitchProxyProps extends Omit<SwitchProps, 'defaultValue' | 'onChange'> {
  id?: string;
  name?: string;
  onChange: InputProxyOnChangeEvent<boolean, MouseEvent>;
  renderType?: InputRenderType;
  value: unknown;
}

const SwitchProxy = ({ id, name, onChange, checked, value, renderType, ...props }: SwitchProxyProps) => {
  const handleChange = useCallback(
    (checked: boolean, evt: MouseEvent) => {
      onChange(checked, name, id, evt);
    },
    [id, name, onChange]
  );

  /* coalesce into checked value */
  const isChecked = typeof value === 'boolean' ? value : checked;
  return <Switch onChange={handleChange} checked={isChecked} {...props} />;
};

export default SwitchProxy;
