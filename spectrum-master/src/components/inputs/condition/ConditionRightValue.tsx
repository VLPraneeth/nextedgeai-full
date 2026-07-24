//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import { isEqual } from 'lodash';
import { ChangeEvent, memo, useEffect, useState } from 'react';

import InputContainer from 'components/inputs/InputContainer';
import { LeftValue, RightOption, OperatorValue, PicklistValue, RightValue } from 'components/inputs/types';
import usePreviousValue from 'hooks/usePreviousValue';
import { isTokenSupportedDatatype } from 'pages/sync-studio/node-config/utils';
import AppConstants from 'utils/AppConstants';
import { getDefaultRHSValue } from 'utils/InputUtil';

import TokenizableFieldGroup from '../TokenizableFieldGroup';

import './ConditionRightValue.less';

interface ConditionRightValueProps {
  leftValue?: LeftValue;
  operatorValue?: OperatorValue;
  value?: RightValue;
  values?: PicklistValue[];
  defaultValue?: RightValue;
  onChange?: (value: RightValue) => void;
  name: string;
  showTokens?: boolean;
  disabled?: boolean;
  displayMode?: string;
  rightOptions?: RightOption[];
  placeHolder?: string;
}

// List of input types that the condition right will use the autocomple input. Inputs that need raw value
const AUTOCOMPLETE_INPUT_TYPE_MAP = [AppConstants.INPUT_TYPE.REFERENCE, AppConstants.INPUT_TYPE.PICKLIST];

const isInAutoCompleteInputTypeMap = (datatype: any): datatype is typeof AUTOCOMPLETE_INPUT_TYPE_MAP => {
  return AUTOCOMPLETE_INPUT_TYPE_MAP.includes(datatype);
};

// Override the operator datatype for specific left render type
const LEFT_VALUE_RENDER_TYPE_OVERRIDE: string[] = [AppConstants.INPUT_RENDER_TYPE.DATASET_VARIABLE_PICKER];
const isLeftValueRenderTypeOverride = (renderType: string) => {
  return LEFT_VALUE_RENDER_TYPE_OVERRIDE.includes(renderType);
};

const ConditionRightValue = memo(
  ({
    leftValue,
    operatorValue,
    values,
    defaultValue,
    onChange,
    name,
    disabled,
    showTokens = false,
    displayMode,
    rightOptions,
    placeHolder,
  }: ConditionRightValueProps) => {
    const [value, setValue] = useState(defaultValue);

    const previousOperatorValue = usePreviousValue(operatorValue);

    // Reset the RHS value when the operator changes
    useEffect(() => {
      if (previousOperatorValue && !isEqual(previousOperatorValue?.value, operatorValue?.value)) {
        // Always clear the value when operator changes to avoid mixing different value types
        const newRightValue = {
          type: value?.type ?? 'literal',
          value: undefined,
        };

        setValue(newRightValue);
        onChange?.(newRightValue);
      }
    }, [operatorValue?.value, onChange, value?.type]);

    const onRightChange = (evt?: ChangeEvent<HTMLInputElement> | string | boolean) => {
      if (typeof evt === 'object' && typeof evt?.persist === 'function') {
        evt.persist();
      }

      let val;

      if (Array.isArray(evt) && rightOptions?.length) {
        val = evt.length ? [(evt as string[]).slice().pop()] : [];
      } else if (typeof evt === 'string' || typeof evt === 'boolean' || Array.isArray(evt)) {
        if (leftValue?.renderType && isLeftValueRenderTypeOverride(leftValue.renderType)) {
          // Current left value render types overrides only expect text
          val = evt;
        } else if (operatorValue?.datatype === AppConstants.INPUT_TYPE.MULTIVALUETEXT && typeof evt === 'string') {
          // When the event is a string and the datatype is `multivaluetext` then
          // set the value to an array, not a string
          if (value?.value && Array.isArray(value?.value)) {
            // Remove the selected item from the current array to avoid duplicates
            const filteredValue = value?.value.filter((item) => item !== evt);
            val = [...filteredValue, evt];
          } else {
            val = [evt];
          }
        } else {
          val = evt;
        }
      } else if (
        typeof evt?.target?.checked !== 'undefined' &&
        leftValue?.datatype === AppConstants.INPUT_TYPE.BOOLEAN
      ) {
        val = evt.target.checked;
      } else if (typeof evt?.target?.value !== 'undefined') {
        val = evt.target.value;
      } else if (!evt?.target && typeof evt === 'object') {
        val = evt;
      }

      const v: RightValue = {
        value: val as any,
        type: 'literal',
      };

      setValue(v);
      onChange && onChange(v);
    };

    // Note: Force to use checkbox for booleans. The default for inputput container
    // datatype boolean is using a switch
    let datatype = leftValue?.datatype || AppConstants.INPUT_TYPE.STRING;
    let renderType;

    if (leftValue?.renderType) {
      renderType = leftValue.renderType;
    } else {
      // TODO: Clean this up in the future and expect a renderType metadata will
      // be coming from the server.
      // Override the datataype boolean to use the checkbox
      if (leftValue?.datatype === AppConstants.INPUT_TYPE.BOOLEAN) {
        datatype = AppConstants.INPUT_TYPE.CHECKBOX;
      } else if (isTokenSupportedDatatype(leftValue?.datatype)) {
        // Override to use the tokens for datatype string.
        renderType = leftValue?.renderType || AppConstants.INPUT_RENDER_TYPE.TOKENS;
      }
    }

    // Note: We are forcing the auto complete for picklist and reference
    // datatypes since the operator values for this right value has contains,
    // starts with, etc... for raw value
    if (leftValue?.datatype && isInAutoCompleteInputTypeMap(leftValue.datatype)) {
      datatype = AppConstants.INPUT_TYPE.AUTOCOMPLETE;
    }

    // Operator datatype overrides the left value data and render types
    if (operatorValue?.datatype) {
      datatype = operatorValue?.datatype;
      if (isTokenSupportedDatatype(operatorValue?.datatype)) {
        renderType = AppConstants.INPUT_RENDER_TYPE.TOKENS;
      } else {
        // clear the picklist renderType if there is an operator override
        renderType = undefined;
      }
    }
    if (operatorValue?.renderType) {
      renderType = operatorValue?.renderType;
    }

    const extraProps = leftValue?.datatype === AppConstants.INPUT_TYPE.BOOLEAN ? { defaultChecked: value?.value } : {};

    // Ignore the operator render type for specific left value render types
    if (leftValue?.renderType && isLeftValueRenderTypeOverride(leftValue.renderType)) {
      renderType = leftValue.renderType;
    }

    const additionalProps: Record<string, any> = {};
    if (rightOptions?.length) {
      datatype = AppConstants.INPUT_TYPE.MULTIVALUETEXT;
      (additionalProps['mode'] = 'tags'), (additionalProps['optionsData'] = rightOptions);
    }

    return (
      <div className="synri-condition-right">
        <TokenizableFieldGroup
          className="synri-condition-right-overflow-auto"
          disableTokens={!showTokens || disabled}
          fallbackOnTokenSelect={(token) => onRightChange(token.token)}
          labelContainerClassName={cx(showTokens && 'synri-condition-right-label-container')}
          labelSiblings={
            /* need some padding while the tokens load */
            showTokens && <div />
          }>
          <InputContainer
            key={`condition-right-input-${name}`}
            className={`synri-condition-right-datatype-${datatype}`}
            datatype={datatype}
            // mode={rightOptions?.length ? 'tags' : undefined}
            {...(rightOptions?.length ? additionalProps : {})}
            defaultValue={value?.value}
            showTokenSelector={false}
            disabled={disabled}
            onChange={onRightChange}
            renderType={renderType}
            value={value?.value}
            values={rightOptions?.length ? rightOptions : values}
            operatorValue={operatorValue}
            leftValue={leftValue}
            displayMode={displayMode}
            placeholder={placeHolder}
            {...extraProps}
          />
        </TokenizableFieldGroup>
      </div>
    );
  }
);

export default ConditionRightValue;
