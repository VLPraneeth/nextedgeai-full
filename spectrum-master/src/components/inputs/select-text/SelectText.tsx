//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import cx from 'classnames';
import { useEffect, useState } from 'react';

import Input from 'components/inputs/Input';
import Select from 'components/inputs/Select';
import { PicklistValue } from 'components/inputs/types';
import { HStack } from 'components/layout';

import './SelectText.less';

export interface SelectTextValue {
  selectValue: string;
  textValue: string;
}

export interface SelectTextProps {
  className?: string;
  selectPicklistValues: PicklistValue[];
  value?: SelectTextValue;
  onChange?: (value: SelectTextValue) => void;
  disabled?: boolean;
}

export const SelectText = ({ className, selectPicklistValues, value, onChange, disabled = false }: SelectTextProps) => {
  const [textValue, setTextValue] = useState(value?.textValue || '');
  const [selectValue, setSelectValue] = useState(value?.selectValue || '');

  useEffect(() => {
    onChange?.({
      selectValue,
      textValue,
    });
  }, [textValue, selectValue, onChange]);

  useEffect(() => {
    setTextValue(value?.textValue || '');
    setSelectValue(value?.selectValue || '');
  }, [value]);

  return (
    <HStack className={cx('synri-select-text', className)} spacing="z">
      <Select
        value={selectValue}
        onChange={(value: string) => {
          setSelectValue(value);
        }}
        disabled={disabled}
        optionData={selectPicklistValues}
        dropdownMatchSelectWidth
      />
      <Input disabled={disabled} value={textValue} onChange={(evt) => setTextValue(evt.target.value)} />
    </HStack>
  );
};

export default SelectText;
