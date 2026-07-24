import { useCallback } from 'react';
import * as React from 'react';

import Input, { InputProps, InputRef } from '../Input';
import { InputProxyOnChangeEvent } from './types';

export interface TextInputProxyProps extends Omit<InputProps, 'onChange'> {
  onChange: InputProxyOnChangeEvent<string>;
}

const TextInputProxy = React.forwardRef<InputRef, TextInputProxyProps>(({ id, name, onChange, ...props }, ref) => {
  const handleChange = useCallback(
    (evt: React.ChangeEvent<HTMLInputElement>) => {
      onChange(evt.target.value, name, id, evt);
    },
    [id, name, onChange]
  );

  return <Input ref={ref} id={id} name={name} onChange={handleChange} {...props} />;
});

export default TextInputProxy;
