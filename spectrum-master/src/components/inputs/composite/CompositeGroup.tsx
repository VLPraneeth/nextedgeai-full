//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon, Tooltip } from 'antd';
import cx from 'classnames';
import { cloneDeep, find, isString, kebabCase } from 'lodash';
import { useCallback, useEffect, useState } from 'react';
import { Draggable } from 'react-beautiful-dnd';

import InputWithLabel from 'components/inputs/InputWithLabel';
import InputContainer from 'components/inputs/InputContainer';
import useDimensions from 'hooks/useDimensions';
import AppConstants from 'utils/AppConstants';
import { getDefaultValue } from 'utils/InputUtil';
import { findConfiguration, findConfigurationByGraphKey, getDependantField } from 'utils/NodeConfigUtil';
import { SingleTokenRegex, TOKEN_BEGIN_SENTINEL, TOKEN_END_SENTINEL } from '../tokens/constants';

import { ConditionProps } from '../condition';
import CompositeOrder from './CompositeOrder';

import './CompositeGroup.less';
import { DataSourceFieldPickerValue } from 'pages/insights-studio/dataset/configuration/DataSourceFieldPicker';
import { useI18nContext } from 'components/I18nProvider';
import { tNamespaced } from 'utils/i18nUtil';

export const RENDER_TYPE_WITHOUT_DEFAULT_VALUE = [AppConstants.INPUT_RENDER_TYPE.TOKENS];
const USER_INPUTED_TOKEN_ID = 'USER_INPUTED_TOKEN';

function getStyle(style: any, snapshot: any) {
  if (!snapshot.isDropAnimating) {
    return style;
  }
  return {
    ...style,
    // cannot be 0
    transitionDuration: `0.5s`,
  };
}

interface CompositeConfiguration extends Record<string, any> {
  width?: string;
}

type OrderingCallbackFn = (index: number) => void;

export interface CompositeGroupProps {
  configuration: CompositeConfiguration[];
  onChange: (id: any, subValue: any) => void;
  onDelete: (id: string) => void;
  layout?: 'row' | 'column';
  value?: any;
  fetchPicklistValues: ConditionProps['fetchPicklistValues'];
  picklistValues: ConditionProps['picklistValues'];
  order?: number;
  onClickDown: OrderingCallbackFn;
  onClickUp: OrderingCallbackFn;
  hideOrderNumber?: boolean;
  hideDelete?: boolean;
}

const CompositeGroup = ({
  configuration,
  onChange,
  onDelete,
  layout = 'row' /* default layout is row */,
  value,
  fetchPicklistValues,
  picklistValues,
  order = 1,
  onClickDown,
  onClickUp,
  hideOrderNumber,
  hideDelete,
}: CompositeGroupProps) => {
  const tn = tNamespaced('Filter');
  const [active, setActive] = useState(false);
  const { repeatId } = value;
  const [measurementRef, dimensions] = useDimensions({ liveMeasure: false });
  const [searchValue, setSearchValue] = useState('');
  const [isTokenInput, setIsTokenInput] = useState(false);

  const onLhsSearchChange = useCallback((input: string) => {
    setSearchValue(input);
  }, []);

  useEffect(() => {
    setIsTokenInput(!!searchValue.match(SingleTokenRegex));
  }, [searchValue]);

  const onGroupChange = (name: string, value: any, ...rest: any[]) => {
    let valueToUse;
    // Handle token input case
    if (isTokenInput && typeof value === 'string') {
      valueToUse = {
        type: 'variable',
        value: value,
        id: USER_INPUTED_TOKEN_ID,
        label: value,
      };
    }

    let transformedValues;
    if (rest?.[1]) {
      transformedValues = { name, value: rest[1] };
    } else if (typeof value === 'string') {
      transformedValues = { name, value };
    } else if (value?.currentTarget?.value) {
      transformedValues = { name, value: value.currentTarget.value };
    } else if (typeof value?.target?.value === 'string') {
      transformedValues = { name, value: value.target.value };
    }
    onChange.apply(undefined, [repeatId, transformedValues || value]);

    _handleDependantField(name, value, configuration);
  };

  const _handleDependantField = (name: any, value: string, configuration: any) => {
    // Check if the changed values is a picklist and has dependsOn. Make the ajax request if any
    const config = findConfiguration(name, { configuration });
    const field = getDependantField(config, { configuration });
    if (field && isString(value)) {
      const options = {
        ...field.dependsOn,
        dependantId: value,
        name: field.name,
      };
      const id = `${options.dependantId}/${options.name}${options.dependantType}`;
      if (picklistValues?.[id]) {
        return picklistValues[id];
      } else {
        fetchPicklistValues?.({ ...options, id });
      }
    }
  };

  const getInputConfig = (inputConfig: any) => {
    let lRestConfig = cloneDeep(inputConfig);
    if (lRestConfig.dependsOn) {
      const graphConfig = findConfigurationByGraphKey(lRestConfig.dependsOn.dependantField, { configuration });
      if (graphConfig) {
        const graphValue = find(value, (val: any) => {
          return val.name === graphConfig.name;
        });
        if (graphValue && graphValue.value) {
          const id = `${graphValue.value}/${lRestConfig.name}${lRestConfig.dependsOn.dependantType}`;
          if (picklistValues?.[id]) {
            lRestConfig.values = picklistValues[id];
          } else {
            fetchPicklistValues?.({ ...lRestConfig.dependsOn, dependantId: graphValue.value, id });
          }
        }
      }
    } else if (RENDER_TYPE_WITHOUT_DEFAULT_VALUE.includes(inputConfig.renderType)) {
      // Force the default value to value for inputs that does not support default value
      lRestConfig.value = getDefaultValue(value[lRestConfig.name]);
    }
    return lRestConfig;
  };

  const onChangeTokenValue = useCallback(
    (val: string | DataSourceFieldPickerValue) => {
      const newVal = {
        ...value.updateField,
        value: val,
      };
      onGroupChange(newVal.name, newVal.value);
    },
    [name, onChange, value]
  );

  const renderTokenizableInput = useCallback(
    (lRestConfig: any) => {
      const { name, label } = lRestConfig;
      const inputValue = getDefaultValue(value[name]);
      const isTokenValue = inputValue?.id === 'USER_INPUTED_TOKEN';
      const displayValue = isTokenValue ? inputValue.value : inputValue;

      return (
        <div className="composite-input-left">
          <div className="composite-input-left-label">{label}</div>
          <InputContainer
            dropdownMatchSelectWidth={false}
            key={`composite-group-token-input-${name}`}
            disabled={lRestConfig.disabled}
            datatype={lRestConfig.type || 'string'}
            values={lRestConfig.values || []}
            value={displayValue}
            defaultValue={getDefaultValue(value[name])}
            onChange={(val: any) => onGroupChange(name, val)}
            onSearchChange={onLhsSearchChange}
            isTokenInput={isTokenInput}
            dropdownRender={(menu: React.ReactNode) => (
              <div>
                {menu}
                <div
                  onMouseDown={() => {
                    onChangeTokenValue(searchValue);
                  }}
                  title={
                    isTokenInput
                      ? `${searchValue}`
                      : // NOTE: there seems to be a bug with i18n when you substute in {{ and }} as string values. So we have to
                        // "pass" the token sentinels in this way instead of the built in way.
                        `${tn('token_value_help_1')}'${TOKEN_BEGIN_SENTINEL}'${tn(
                          'token_value_help_2'
                        )}'${TOKEN_END_SENTINEL}'.`
                  }
                  className={cx(
                    'user-token-condition-option',
                    !isTokenInput && 'user-token-condition-option--disabled'
                  )}>
                  {'Use entered Token Value'}
                  <Tooltip title="Use the entered text as a token">
                    <Icon type="info-circle" theme="filled" className="user-token-condition-option__icon" />
                  </Tooltip>
                </div>
              </div>
            )}
            {...lRestConfig}
          />
        </div>
      );
    },
    [
      onLhsSearchChange,
      isTokenInput,
      searchValue,
      onGroupChange,
      onChangeTokenValue,
      value,
      layout,
      configuration,
      dimensions,
    ]
  );

  const defaultInputWidth = `${layout === 'row' ? 100 / configuration.length : 100}%`;

  return (
    <Draggable draggableId={`composite-group-draggable-${repeatId}`} index={order - 1}>
      {(provided, snapshot) => {
        const maxWidth = layout === 'row' ? dimensions.width / configuration.length : dimensions.width;
        return (
          <div
            {...provided.dragHandleProps}
            {...provided.draggableProps}
            ref={provided.innerRef}
            style={getStyle(provided.draggableProps.style, snapshot)}>
            {/* extra div for smoother drop animation */}
            <div className="synri-composite-group-drag-container">
              <div
                onMouseEnter={() => setActive(true)}
                onMouseLeave={() => setActive(false)}
                className={cx('synri-composite-group', {
                  'synri-composite-row': layout === 'row',
                  'synri-composite-column': layout === 'column',
                  'synri-composite-active': active,
                })}>
                <CompositeOrder
                  order={order}
                  onClickUp={onClickUp}
                  onClickDown={onClickDown}
                  hideOrderNumber={hideOrderNumber}
                />
                <div className="synri-composite-input-container" ref={measurementRef}>
                  {/* configuration should not have default value, ignoring it here */}
                  {configuration.map(({ className, defaultValue: ignored, width, ...restConfig }) => {
                    const lRestConfig = getInputConfig(restConfig);
                    const { name, allowUserToken } = lRestConfig;
                    if (allowUserToken) {
                      return renderTokenizableInput(lRestConfig);
                    }
                    return (
                      <InputWithLabel
                        key={`composite-group-input-${name}`}
                        className={cx('synri-composite-input', `composite-group-input-${name}`, className, {
                          [`synri-render-type-${kebabCase(lRestConfig.renderType)}`]: lRestConfig.renderType,
                        })}
                        style={{ width: width || defaultInputWidth, maxWidth: width ? null : maxWidth }}
                        onChange={onGroupChange.bind(undefined, lRestConfig.name)}
                        defaultValue={getDefaultValue(value[name])}
                        fetchPicklistValues={fetchPicklistValues}
                        picklistValues={picklistValues}
                        tooltip={lRestConfig.helpSummary}
                        {...lRestConfig}
                      />
                    );
                  })}
                </div>
                {!hideDelete && (
                  <div className="synri-delete-container">
                    <Icon
                      className={cx('synri-composite-delete', {
                        'synri-composite-with-label': !!configuration?.find((c: any) => c.label),
                      })}
                      type="delete"
                      theme="filled"
                      onClick={() => onDelete(repeatId)}
                    />
                  </div>
                )}
              </div>
            </div>
          </div>
        );
      }}
    </Draggable>
  );
};

export default CompositeGroup;
