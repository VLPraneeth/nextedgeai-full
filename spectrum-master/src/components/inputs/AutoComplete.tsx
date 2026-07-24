//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { default as AAutoComplete, AutoCompleteProps as AAutoCompleteProps } from 'antd/lib/auto-complete';
import { SelectValue } from 'antd/lib/select';
import cx from 'classnames';
import { memo, useEffect, useMemo, useState } from 'react';

import { PicklistValue } from 'components/inputs/types';

export interface AutoCompleteProps extends AAutoCompleteProps {
  /**
   * Additional classname that will be added to the container of the input tags
   */
  className?: string;
  /**
   * default values of auto complete
   */
  defaultValue?: string | string[];
  /**
   * Picklist values
   */
  values?: PicklistValue[];
}

const AutoComplete = memo(
  ({ className, onChange, defaultValue, values: allValues, dropdownMatchSelectWidth = false }: AutoCompleteProps) => {
    const [dataSource, setDataSource] = useState<string[]>([]);
    const [inputValue, setInputValue] = useState<string | string[]>('');

    // Auto complete will crash if the values are
    const values = useMemo(() => allValues?.filter((val) => Boolean(val.label || val.value)), [allValues]);

    if (typeof defaultValue !== 'string') {
      try {
        defaultValue = JSON.stringify(defaultValue);
      } catch (e) {
        defaultValue = '';
      }
    }

    useEffect(() => {
      if (values && values?.length > 0) {
        setDataSource(values.map((val: PicklistValue) => val.label || val.value || ''));
      }
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [values]);

    useEffect(() => {
      let fVAlue;
      if (values) {
        fVAlue = values?.find((value) => value.value === defaultValue);
      }
      if (fVAlue?.label) {
        setInputValue(fVAlue.label);
      } else {
        setInputValue(defaultValue || '');
      }
    }, [defaultValue, values]);

    const filterValues = (value?: PicklistValue, searchText?: string | string[]) => {
      // Match all if blank search text
      if (!searchText) {
        return true;
      }
      // Do not match if value is missing
      if (!value) {
        return false;
      }
      if (typeof searchText !== 'string') {
        return false;
      }
      return (
        (value?.label && value?.label.toLowerCase().indexOf(searchText?.toLowerCase()) > -1) ||
        (value?.value && value?.value.toLowerCase().indexOf(searchText?.toLowerCase()) > -1)
      );
    };

    const onSearch = (searchText?: string) => {
      if (searchText && values && values?.length > 0) {
        setDataSource(
          values
            .filter((val: PicklistValue) => filterValues(val, searchText))
            .map((val: PicklistValue) => {
              return val.label || val.value || '';
            })
        );
      }
    };

    const onSelect = (value: SelectValue) => {
      const match = values?.find((val: PicklistValue) => {
        return val.label === value;
      });
      if (match && match.value && match.label) {
        setInputValue(match.label);
        onChange && onChange(match.value);
      }
    };

    const onValueChange = (searchValue: SelectValue) => {
      if (typeof searchValue === 'string') {
        // If user types the label of a value, trigger an onChange with that
        // values value. Otherwise use the free form searchValue.
        const searchString = searchValue.toLowerCase();
        const fVAlue = values?.find((value) => value.label?.toLowerCase() === searchString);

        const newInputValue = fVAlue?.label || searchValue;
        const newValue = fVAlue?.value || searchValue;

        setInputValue(newInputValue);
        onChange && onChange(newValue);
      }
    };

    return (
      <AAutoComplete
        className={cx('synri-auto-complete', className)}
        dataSource={dataSource}
        value={inputValue}
        onSelect={onSelect}
        onChange={onValueChange}
        dropdownMatchSelectWidth={dropdownMatchSelectWidth}
        onSearch={onSearch}
      />
    );
  }
);

export default AutoComplete;
