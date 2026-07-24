import AntInput, { TextAreaProps as AntTextAreaProps } from 'antd/lib/input';
import { useCallback } from 'react';
import * as React from 'react';

import { InputProxyOnChangeEvent } from './types';

const TextArea = AntInput.TextArea;

export interface TextAreaProxyProps extends Omit<AntTextAreaProps, 'onChange'> {
  onChange: InputProxyOnChangeEvent<string, React.ChangeEvent<HTMLTextAreaElement>>;
}

const TextAreaProxy = ({ id, name, onChange, rows = 4, ...props }: TextAreaProxyProps) => {
  const handleChange = useCallback(
    (evt: React.ChangeEvent<HTMLTextAreaElement>) => {
      onChange(evt.target.value, name, id, evt);
    },
    [id, name, onChange]
  );

  return <TextArea id={id} name={name} onChange={handleChange} rows={rows} {...props} />;
};

export default TextAreaProxy;
