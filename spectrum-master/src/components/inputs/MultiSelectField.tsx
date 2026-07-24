//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { SelectProps } from 'antd/lib/select';
import cx from 'classnames';

import { useFieldOptions, FieldItem } from 'components/inputs/FieldOptions';
import { EMPTY_ARRAY } from 'store/constants';
import { EntityField } from 'store/entity/types';
import AppConstants from 'utils/AppConstants';

import Select from '../inputs/Select';

export interface MultiValueFieldProps extends SelectProps<string[]> {
  className?: string;
  displayMode: string;
  mode?: SelectProps['mode'];
  values?: EntityField[];
}

const MultiSelectField = ({ values, className, displayMode, mode = 'tags', ...rest }: MultiValueFieldProps) => {
  const fieldOptions = useFieldOptions(values || EMPTY_ARRAY);

  if (
    displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY &&
    (Array.isArray(rest.value) || Array.isArray(rest.defaultValue))
  ) {
    const displayValue = rest.value || rest.defaultValue;
    return (
      <>
        {values
          ?.filter((value) => displayValue?.includes(value.id))
          .map((value) => (
            <FieldItem key={value.id} {...value} />
          ))}
      </>
    );
  } else {
    return (
      <Select
        className={cx('synri-multi-value-field-select', className)}
        dropdownMatchSelectWidth
        disabled={displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY}
        mode={mode}
        {...fieldOptions}
        {...rest}
      />
    );
  }
};

export default MultiSelectField;
