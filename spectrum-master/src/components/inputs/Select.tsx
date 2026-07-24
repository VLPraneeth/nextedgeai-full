//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon, Tooltip } from 'antd';
import ASelect, { OptionProps, SelectProps as AntSelectProps, SelectValue } from 'antd/lib/select';
import cx from 'classnames';
import { each, map, partition } from 'lodash';
import { ReactElement, useState } from 'react';

import FieldTypeBadge from 'components/FieldTypeBadge';
import { DisplayMode, PicklistValue, PicklistValueServer } from 'components/inputs/types';
import { Text } from 'components/typography';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';
import { getTextWidth } from 'utils/GraphUtil';
import { copyStringToClipboard } from 'utils/StringUtil';

import { USER_INPUTED_TOKEN_ID } from './condition';

import './Select.less';

export const { Option } = ASelect;

export interface SelectProps<ValueType extends SelectValue = SelectValue> extends AntSelectProps<ValueType> {
  children?: React.ReactNode;
  datatype?: string;
  displayMode?: DisplayMode;
  optionData?: PicklistValue[];
  options?: JSX.Element[];
  useDataTypeBadges?: boolean;
  values?: PicklistValue[];
  onSearchChange?: (input: string) => void;
  isTokenInput?: boolean;
}

const Select = <ValueType extends SelectValue = SelectValue>({
  children,
  className,
  datatype,
  dropdownRender,
  displayMode,
  isTokenInput,
  optionData,
  options,
  onSearchChange,
  useDataTypeBadges,
  values,
  ...rest
}: SelectProps<ValueType>) => {
  const [searchValue, setSearchValue] = useState('');

  if (displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY) {
    let value = rest.value?.toString() || rest.defaultValue?.toString() || '';

    if (values) {
      value = values
        .filter(
          (item) => item.value === (rest.value ?? rest.defaultValue) || (rest.value ?? rest.defaultValue) === item.value
        )
        .map(({ label }) => label)
        .join(', ');
    }
    return <Text>{value}</Text>;
  }

  const _getOptions = () => {
    let hasGroups = !!(optionData && optionData[0]?.picklistGroup);
    let options: JSX.Element[] = [];

    const [tokenDataArray, filteredDataArray] = partition(
      optionData,
      (data: PicklistValue) => data.id === USER_INPUTED_TOKEN_ID
    );
    const userTokenData = tokenDataArray[0];

    if (hasGroups) {
      const groups: Record<string, PicklistValue[]> = {};
      each(filteredDataArray, (val) => {
        const { picklistGroup } = val;
        if (!picklistGroup) {
          return;
        }
        if (!groups[picklistGroup]) {
          groups[picklistGroup] = [];
        }
        groups[picklistGroup].push({
          ...val,
        });
      });

      each(groups, (groupOptions, key) => {
        options.push(
          <Option
            className="ant-select-dropdown-menu-item-group-title option-group"
            title={key}
            key={key}
            value={key}
            disabled>
            {key}
          </Option>
        );
        each(groupOptions, (groupOption) => {
          const { value, label } = groupOption;
          const title = `${label} (${key})`;
          const dataType = getDataTypeFromValue(groupOption);
          options.push(
            <Option key={value} className="option-group-item" value={value}>
              {dataType && useDataTypeBadges ? (
                <div style={{ display: 'flex' }}>
                  <FieldTypeBadge dataType={dataType} description={dataType} size="small" />
                  <span title={title}>{label}</span>
                </div>
              ) : (
                label
              )}
            </Option>
          );
        });
      });
    } else {
      options = map(filteredDataArray, (opt) => {
        const { value, label } = opt;
        const dataType = getDataTypeFromValue(opt);
        return (
          <Option key={value} value={value} title={label}>
            {dataType && useDataTypeBadges ? (
              <div style={{ display: 'flex' }}>
                <FieldTypeBadge dataType={dataType} description={dataType} size="small" />
                <span>{label}</span>
              </div>
            ) : (
              label
            )}
          </Option>
        );
      });
    }

    if (userTokenData) {
      const { value } = userTokenData;

      options.push(
        <Option
          key={USER_INPUTED_TOKEN_ID}
          value={value}
          className={cx(
            'user-inputed-token-option',
            // We don't want to show the value in the options list at any point.
            (rest.defaultValue !== value || searchValue === value) && 'user-inputed-token-option--hidden'
          )}
          disabled={!isTokenInput}>
          {value}
          <Tooltip title={'This is a user entered token value.'}>
            <Icon type="info-circle" theme="filled" className="user-inputed-token-option__icon" />
          </Tooltip>
          <span className="user-inputed-token-option__copy">
            <Icon
              type="copy"
              theme="outlined"
              onClick={(e) => {
                e.stopPropagation();
                copyStringToClipboard(value);
              }}
            />
          </span>
          <Tooltip title={value}>
            <div style={{ width: getTextWidth(`{{${value}}}`) }} className="user-inputed-token-option__tooltip" />
          </Tooltip>
        </Option>
      );
    }

    return options;
  };

  return (
    // @ts-expect-error: ...rest causing a type error
    <ASelect<ValueType>
      className={cx('synri-select', className)}
      dropdownMatchSelectWidth={false}
      dropdownRender={dropdownRender}
      onSearch={(input: string) => {
        setSearchValue(input);
        onSearchChange?.(input);
      }}
      filterOption={(input, option: ReactElement) => {
        const displayedValue = Array.isArray(option.props.children?.props?.children)
          ? getChildrenDisplayValue(option.props.children.props.children)
          : option.props.children?.toString();

        // Never show the user token option in the search results.
        return !!displayedValue?.toLowerCase()?.includes(input.toLowerCase());
      }}
      showSearch
      {...rest}
      mode={datatype === AppConstants.INPUT_TYPE.MULTISELECT ? 'multiple' : rest.mode}>
      {children ?? options ?? _getOptions()}
    </ASelect>
  );
};

// Handle both server side and ui side datatype cases on picklist options.
const getDataTypeFromValue = (value: PicklistValue | PicklistValueServer) => {
  if ('datatype' in value) {
    return value.datatype;
  } else if ('dataType' in value) {
    return value.dataType;
  }
};

export const getChildrenDisplayValue = (children?: ReactElement[]) => {
  if (!Array.isArray(children)) {
    return '';
  }
  const displayValue =
    children?.map((child) => {
      // Antd passes down children as string. We're handling that here as well.
      if (child?.props?.children && typeof child?.props?.children === 'string') {
        // Support for multiple element in the select
        return child.props.children;
      } else if (child?.props?.title) {
        return child.props.children;
      }
      return undefined;
    }) || EMPTY_ARRAY;
  return displayValue.filter(Boolean).join(' ');
};

export default Select;
export type { OptionProps };
