import { useCallback } from 'react';
import * as React from 'react';

import ScheduleInput, { ScheduleInputProps } from '../schedule/ScheduleInput';
import { InputProxyOnChangeEvent } from './types';

export interface ScheduleInputProxyProps extends Omit<ScheduleInputProps, 'defaultValue' | 'onChange'> {
  id?: string;
  name?: string;
  onChange: InputProxyOnChangeEvent;
  value: string;
}

const ScheduleInputProxy = ({ id, name, onChange, ...props }: ScheduleInputProxyProps) => {
  const handleChange = useCallback(
    (evt: React.ChangeEvent<HTMLInputElement>) => {
      onChange(evt.target.value, name, id, evt);
    },
    [id, name, onChange]
  );

  return <ScheduleInput onChange={handleChange} {...props} />;
};

export default ScheduleInputProxy;
