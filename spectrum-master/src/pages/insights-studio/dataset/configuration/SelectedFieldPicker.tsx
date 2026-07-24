//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import ASelect, { SelectProps as AntSelectProps, SelectValue } from 'antd/lib/select';
import cx from 'classnames';
import { ReactElement, useState } from 'react';

import Select from 'components/inputs/Select';
import { PicklistValue } from 'components/inputs/types';
import { createIdWithAlias, splitIdAndAlias } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { useDataSourceFields } from 'pages/insights-studio/utils/useDataSourceFields';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';

import './SelectedFieldPicker.scss';
export const { Option, OptGroup } = ASelect;

export interface SelectProps<Option extends SelectValue = SelectValue>
  extends Omit<AntSelectProps<Option>, 'onChange' | 'value' | 'defaultValue'> {
  options?: ReactElement;
  value?: SelectedFieldPickerValue;
  defaultValue?: SelectedFieldPickerValue;
  onChange: (value: SelectedFieldPickerValue) => void;
  optionsKey?: string;
}

export interface SelectedFieldPickerValue {
  id: string;
  datasourceAlias?: string;
  name?: string;
}

export const SelectedFieldPicker = <Option extends SelectValue = SelectValue>({
  options,
  value,
  defaultValue,
  onChange,
  optionsKey,
  disabled,
}: SelectProps<Option>) => {
  const [searchText, setSearchText] = useState('');
  const { sortDataSourceFields } = useDataSourceFields({ searchText: '' });

  const { selectedDataSourceFields } = useUnifiedDataCardAuthoring();

  const selectedFields = selectedDataSourceFields?.entities.flatMap((entity) => entity.fields);

  let optionsData: PicklistValue[] = [];

  sortDataSourceFields.forEach((field) => {
    if (!field.datasetId) {
      optionsData.push({ label: field.displayName ?? '', value: field.id, picklistGroup: field.picklistGroup });
    } else {
      const selectedField = selectedFields?.find(
        (selectedfield) => field.id === selectedfield.id && field.datasourceAlias === selectedfield.datasourceAlias
      );

      if (selectedField) {
        optionsData.push({
          label: field.displayName || '',
          value: createIdWithAlias(field.id, selectedField.datasourceAlias),
          picklistGroup: field.picklistGroup || 'Entity Fields',
          dataType: field.dataType,
        });
      }
    }
  });

  if (searchText) {
    optionsData = optionsData.filter(
      (option) => !!option && option.label?.toLowerCase().includes(searchText.toLowerCase())
    );
  }

  const onSelectChange = (val: SelectValue) => {
    setSearchText('');
    const _val = val.toString();
    const { id, datasourceAlias } = splitIdAndAlias(_val);
    onChange({ name: 'field', id, datasourceAlias });
  };

  const _defaultValue = createIdWithAlias(defaultValue?.id, defaultValue?.datasourceAlias);
  const _value = createIdWithAlias(value?.id, value?.datasourceAlias);

  return (
    <div className="selected-field-picker">
      <Select
        disabled={disabled}
        placeholder="Select a field..."
        className={cx('data-source-field-picker__select')}
        onChange={onSelectChange}
        defaultValue={_defaultValue}
        value={value ? _value : _defaultValue}
        optionData={optionsData}
        showSearch
        onSearch={(text: string) => setSearchText(text?.trim())}
        filterOption={false}
        dropdownClassName="selected-field-picker__dropdown"
        useDataTypeBadges
      />
    </div>
  );
};
