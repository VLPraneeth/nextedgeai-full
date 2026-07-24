//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import ASelect, { SelectProps as AntSelectProps, SelectValue } from 'antd/lib/select';
import cx from 'classnames';
import { ReactElement, useState } from 'react';

import { createIdWithAlias, splitIdAndAlias } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useDataSourceFields } from 'pages/insights-studio/utils/useDataSourceFields';
import './DataSourceFieldPicker.less';

export const { Option, OptGroup } = ASelect;

export interface SelectProps<Option extends SelectValue = SelectValue>
  extends Omit<AntSelectProps<Option>, 'onChange' | 'value' | 'defaultValue'> {
  options?: ReactElement;
  value?: DataSourceFieldPickerValue;
  defaultValue?: DataSourceFieldPickerValue;
  onChange: (value: DataSourceFieldPickerValue) => void;
  optionsKey?: string;
}

export interface DataSourceFieldPickerValue {
  id: string;
  value: string;
  datasourceAlias?: string;
  name?: string;
}

export const DataSourceFieldPicker = <Option extends SelectValue = SelectValue>({
  options,
  value,
  defaultValue,
  onChange,
  optionsKey,
  disabled,
}: SelectProps<Option>) => {
  const [searchText, setSearchText] = useState('');
  const { availableSelectOptions, sortSelectOptions } = useDataSourceFields({ searchText });

  const onSelectChange = (val: string) => {
    setSearchText('');
    const { id, datasourceAlias } = splitIdAndAlias(val);
    onChange({ name: 'filter', id, datasourceAlias, value: id });
  };

  const _defaultValue = createIdWithAlias(defaultValue?.id, defaultValue?.datasourceAlias);
  const _value = createIdWithAlias(value?.id, value?.datasourceAlias);

  return (
    <div className="data-source-field-picker">
      <ASelect
        showSearch
        disabled={disabled}
        placeholder="Select a field..."
        className={cx('data-source-field-picker__select')}
        onChange={onSelectChange}
        defaultValue={_defaultValue}
        value={value ? _value : _defaultValue}
        onSearch={(text) => setSearchText(text?.trim())}
        // Always perform our own search
        filterOption={() => true}>
        {optionsKey && ['sort', 'group'].includes(optionsKey.toLowerCase())
          ? sortSelectOptions
          : availableSelectOptions}
      </ASelect>
    </div>
  );
};
