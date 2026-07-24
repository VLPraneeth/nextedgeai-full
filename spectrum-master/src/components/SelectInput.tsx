import AntSelect, { SelectProps as AntSelectProps } from 'antd/lib/select';
import cx from 'classnames';
import * as React from 'react';

import './SelectInput.less';

interface BaseOption {
  label: string;
  value: string;
  labelComponent?: React.ReactNode;
}

interface OptionWithExtraData<ExtraData extends unknown> extends BaseOption {
  extraData: ExtraData;
}

export type Option<Extra extends unknown = any> = Extra extends never | undefined
  ? BaseOption
  : OptionWithExtraData<Extra>;

export interface SelectInputProps<OptionItem extends BaseOption = BaseOption> extends AntSelectProps<string> {
  options: OptionItem[];
  renderOption?: (option: OptionItem) => React.ReactNode;
}

const SelectInput = <OptionItem extends BaseOption = BaseOption>({
  className,
  options,
  dropdownMatchSelectWidth = true,
  renderOption,
  ...props
}: SelectInputProps<OptionItem>) => {
  return (
    <AntSelect
      className={cx('synri-select-input', className)}
      dropdownMatchSelectWidth={dropdownMatchSelectWidth}
      {...props}>
      {options.map((option) => (
        <AntSelect.Option key={option.value} value={option.value} title={option.label}>
          {typeof renderOption === 'function' ? renderOption(option) : option.labelComponent || option.label}
        </AntSelect.Option>
      ))}
    </AntSelect>
  );
};

export default SelectInput;
