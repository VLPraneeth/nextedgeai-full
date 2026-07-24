import AntInput, { InputProps as AntInputProps } from 'antd/lib/input';
import { useCallback } from 'react';
import * as React from 'react';

import { InputProxyOnChangeEvent } from './types';

const PasswordInput = AntInput.Password;

export interface PasswordInputProxyProps extends Omit<AntInputProps, 'onChange'> {
  onChange: InputProxyOnChangeEvent<string>;
}

const PasswordInputProxy = ({ id, name, onChange, ...props }: PasswordInputProxyProps) => {
  const handleChange = useCallback(
    (evt: React.ChangeEvent<HTMLInputElement>) => {
      onChange(evt.target.value, name, id, evt);
    },
    [id, name, onChange]
  );

  return <PasswordInput id={id} name={name} onChange={handleChange} {...props} />;
};

export default PasswordInputProxy;
