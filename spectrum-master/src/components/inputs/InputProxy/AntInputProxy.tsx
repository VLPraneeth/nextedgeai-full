import AntInput, { InputProps as AntInputProps } from 'antd/lib/input';
import { useCallback } from 'react';
import * as React from 'react';

import { InputProxyOnChangeEvent } from './types';

export interface AntInputProxyProps extends Omit<AntInputProps, 'onChange'> {
  onChange: InputProxyOnChangeEvent;
}

const AntInputProxy = ({ id, name, onChange, ...props }: AntInputProxyProps) => {
  const handleChange = useCallback(
    (evt: React.ChangeEvent<HTMLInputElement>) => {
      onChange(evt.target.value, name, id, evt);
    },
    [id, name, onChange]
  );

  return <AntInput id={id} name={name} onChange={handleChange} {...props} />;
};

export default AntInputProxy;
