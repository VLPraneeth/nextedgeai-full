//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { SelectValue } from 'antd/lib/select';
import { useCallback } from 'react';

import AutoComplete, { AutoCompleteProps } from 'components/inputs/AutoComplete';
import { PicklistValue } from 'components/inputs/types';
import { EMPTY_ARRAY } from 'store/constants';

import { InputProxyOnChangeEvent } from './types';

export interface OptionData extends PicklistValue {
  picklistGroup?: string;
}

export interface AutoCompleteProxyProps extends Omit<AutoCompleteProps, 'onChange'> {
  onChange: InputProxyOnChangeEvent<SelectValue, never>;
  name: string;
  optionData: OptionData[];
}

const AutoCompleteProxy = ({ id, name, onChange, values, optionData, ...props }: AutoCompleteProxyProps) => {
  const handleChange = useCallback(
    (value: SelectValue) => {
      return onChange(value, name, id);
    },
    [id, name, onChange]
  );

  return <AutoComplete id={id} onChange={handleChange} values={values || optionData || EMPTY_ARRAY} {...props} />;
};

export default AutoCompleteProxy;
