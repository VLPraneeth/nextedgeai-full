//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import Select, { SelectProps, OptionProps } from 'antd/lib/select';
import cx from 'classnames';
import { useMemo, useCallback } from 'react';
import * as React from 'react';

import AppConstants from 'utils/AppConstants';
import { createUniqueEntityTitle } from 'utils/FieldUtil';

import './InstancePicker.less';

const { Option } = Select;

export interface InstanceValue {
  instanceName: string;
  subscriptionName: string;
  value: string;
}
export interface InstancePickerProps extends SelectProps<string[]> {
  className?: string;
  displayMode: string;
  values?: InstanceValue[];
}

export interface InstanceOption {
  id: string;
  instanceName: string;
  subscriptionName: string;
}

const makeInstanceOption = ({ id, instanceName, subscriptionName }: InstanceOption) => {
  return (
    <Option key={id} value={id} title={createUniqueEntityTitle(instanceName, subscriptionName)}>
      <div className="synri-field-option">
        <span className="synri-field-option-display-name">{instanceName}</span>
        <span className="synri-field-option-api-name">({subscriptionName})</span>
      </div>
    </Option>
  );
};

const InstancePicker = ({ values, className, displayMode, ...rest }: InstancePickerProps) => {
  const instanceOptions = useMemo(() => {
    return values?.map((value) =>
      makeInstanceOption({
        id: value.value,
        instanceName: value.instanceName,
        subscriptionName: value.subscriptionName,
      })
    );
  }, [values]);

  const filterOption = useCallback(
    (inputValue: string, option: React.ReactElement<OptionProps>) => {
      const lowerCaseInput = inputValue.toLowerCase();
      const instance = values?.find((instance) => instance.value === option.props.value);
      return (
        !!instance?.instanceName.toLowerCase().includes(lowerCaseInput) ||
        !!instance?.subscriptionName.toLowerCase().includes(lowerCaseInput)
      );
    },
    [values]
  );

  return (
    <Select
      className={cx('synri-instance-picker', className)}
      dropdownMatchSelectWidth
      disabled={displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY}
      mode="multiple"
      filterOption={filterOption}
      {...rest}>
      {instanceOptions}
    </Select>
  );
};

export { InstancePicker };
