import { useCallback } from 'react';
import * as React from 'react';

import TokenTextArea, { TokenTextAreaRef, TokenTextAreaProps } from '../tokens/TokenTextArea';
import { InputProxyOnChangeEvent } from './types';

export interface TokensTextAreaProxyProps extends Omit<TokenTextAreaProps, 'onChange'> {
  onChange: InputProxyOnChangeEvent<string, never>;
}

const TokensTextAreaProxy = React.forwardRef<TokenTextAreaRef, TokensTextAreaProxyProps>(
  ({ id, name, onChange, rows = 4, value, ...props }, ref) => {
    const handleChange = useCallback(
      (evt: React.ChangeEvent<HTMLInputElement>) => {
        onChange(evt.target.value, name, id);
      },
      [id, name, onChange]
    );

    return <TokenTextArea onChange={handleChange} ref={ref} id={id} name={name} rows={rows} value={value} {...props} />;
  }
);

export default TokensTextAreaProxy;
