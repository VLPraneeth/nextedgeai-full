import { SelectValue } from 'antd/lib/select';
import { useCallback } from 'react';

import Select, { SelectProps } from 'components/inputs/Select';

import { InputProxyOnChangeEvent } from './types';

export interface SelectProxyProps<ValueType extends SelectValue = SelectValue>
  extends Omit<SelectProps<ValueType>, 'onChange'> {
  onChange: InputProxyOnChangeEvent<ValueType, never>;
  name: string;
}

const SelectProxy = <ValueType extends SelectValue = SelectValue>({
  id,
  name,
  onChange,
  ...props
}: SelectProxyProps<ValueType>) => {
  const handleChange = useCallback(
    (value: ValueType) => {
      return onChange(value, name, id);
    },
    [id, name, onChange]
  );

  return <Select<ValueType> id={id} onChange={handleChange} {...props} />;
};

export default SelectProxy;
