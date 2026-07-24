//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon, Tooltip } from 'antd';
import cx from 'classnames';
import { isUndefined } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputContainer from 'components/inputs/InputContainer';
import {
  ConditionValue,
  InputDataType,
  LeftValue,
  OperatorValue,
  PicklistValue,
  PicklistValues,
  RightValue,
  RightOption,
} from 'components/inputs/types';
import { HStack } from 'components/layout';
import { DataSourceFieldPickerValue } from 'pages/insights-studio/dataset/configuration/DataSourceFieldPicker';
import { canShowToken, isTokenSupportedDatatype } from 'pages/sync-studio/node-config/utils';
import AppConstants from 'utils/AppConstants';

import FieldGroup from '../FieldGroup';
import { SingleTokenRegex, TOKEN_BEGIN_SENTINEL, TOKEN_END_SENTINEL } from '../tokens/constants';
import ConditionReadOnly from './ConditionReadOnly';
import ConditionRightValue from './ConditionRightValue';
import HiddenLabel from './HiddenLabel';

import './index.less';

const { READONLY } = AppConstants.INPUT_DISPLAY_MODE;

const DEPENDANT_TYPE_OPERATOR = 'Operator';
const DEPENDANT_TYPE_RIGHT = 'Right';

export const USER_INPUTED_TOKEN_ID = 'USER_INPUTED_TOKEN';

// List of input types that the condition right will use the autocomple input. Inputs that need raw value
const NEED_PICKLIST_VALUES_INPUT_TYPES = [
  AppConstants.INPUT_TYPE.REFERENCE,
  AppConstants.INPUT_TYPE.PICKLIST,
  AppConstants.INPUT_TYPE.REFERENCE_DATA,
];

const needPicklistValues = (datatype: any): datatype is typeof NEED_PICKLIST_VALUES_INPUT_TYPES => {
  return NEED_PICKLIST_VALUES_INPUT_TYPES.includes(datatype);
};

function getValueForDatatypeAndOperator(
  currentValue: undefined | RightValue,
  datatype?: InputDataType,
  operator?: OperatorValue
): RightValue | undefined {
  if (operator?.unary) {
    return { type: 'literal', value: '' } as const;
  }

  if (currentValue) {
    return currentValue;
  }

  switch (datatype) {
    case 'boolean':
    case 'checkbox':
      return { type: 'literal', value: false } as const;
  }
}

const userLhsTokenDefaults = {
  id: USER_INPUTED_TOKEN_ID,
  datatype: 'string',
  type: 'variable',
};

export interface FetchPicklistParams {
  id?: string;
  dependantType?: string;
  operatorType?: string;
  dependantId?: string;
  params?: Record<string, any>;
}

export interface LHSTokenOption {
  inputCapturer?: React.Dispatch<React.SetStateAction<string>>;
  isTokenInput?: boolean;
}

export interface ConditionProps {
  name: string;
  className?: string;
  leftDatatype?: string;
  leftRenderType?: string;
  onChange?: (value: ConditionValue) => void;
  defaultValue?: ConditionValue;
  values?: LeftValue[];
  fetchPicklistValues?: (param: FetchPicklistParams) => void;
  picklistValues: PicklistValues<PicklistValue[]>;
  displayMode?: string;
  operatorType?: string;
  rightType?: string;
  lhsDisabled?: boolean;
  allowUserToken?: boolean;
  rightValues?: RightOption[];
  allFieldsDisabled?: boolean;
  hideLeftInput?: boolean;
  verticalLayout?: boolean;
  placeHolder?: string;
}

const Condition = ({
  className,
  leftDatatype,
  leftRenderType,
  onChange,
  name,
  defaultValue = {},
  values,
  fetchPicklistValues,
  picklistValues,
  displayMode,
  operatorType = DEPENDANT_TYPE_OPERATOR,
  rightType = DEPENDANT_TYPE_RIGHT,
  lhsDisabled,
  allowUserToken,
  rightValues: rightOptions,
  allFieldsDisabled,
  hideLeftInput,
  verticalLayout,
  placeHolder,
}: ConditionProps) => {
  const { tn } = useI18nContext();
  const [value, setValue] = useState<ConditionValue>(() => ({ ...defaultValue, name }));

  // Operator picklist values
  const [operatorValues, setOperatorValues] = useState<OperatorValue[]>();
  const [rightValues, setRightValues] = useState<PicklistValue[]>();
  const [isUnary, setIsUnary] = useState(false);
  const [rightDisplayValue, setRightDisplayValue] = useState<string | React.ReactNode>('');
  // Operator selected picklist value
  const [operatorValue, setOperatorValue] = useState<OperatorValue>();

  const [doneFirstRender, setDoneFirstRender] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const [isTokenInput, setIsTokenInput] = useState(defaultValue.left?.id === USER_INPUTED_TOKEN_ID);
  const [leftValues, setLeftValues] = useState(() => {
    if (!allowUserToken || isUndefined(values) || defaultValue.left?.id !== USER_INPUTED_TOKEN_ID) {
      return values;
    }

    return [
      ...values,
      {
        ...userLhsTokenDefaults,
        label: defaultValue.left?.value,
        value: defaultValue.left?.value,
      } as LeftValue,
    ];
  });

  useEffect(() => {
    if (allowUserToken) {
      if (doneFirstRender) {
        setLeftValues((previousValues) => {
          const filteredLeftValues = previousValues?.filter((value) => value.id !== USER_INPUTED_TOKEN_ID);

          return filteredLeftValues
            ? [
                ...filteredLeftValues,
                {
                  ...userLhsTokenDefaults,
                  label: searchValue,
                  value: searchValue,
                } as LeftValue,
              ]
            : previousValues;
        });
      } else {
        setDoneFirstRender(true);
      }
    } else {
      setLeftValues(values);
    }
  }, [doneFirstRender, searchValue, allowUserToken, values]);

  useEffect(() => {
    if (allowUserToken && doneFirstRender) {
      setIsTokenInput(!!searchValue.match(SingleTokenRegex));
    }
  }, [allowUserToken, doneFirstRender, searchValue]);

  useEffect(() => {
    if (value?.left?.value) {
      _fetchPicklistValues({
        dependantType: operatorType,
        dependantId: value.left.value,
        params: {
          datatype: value.left?.datatype,
        },
      });

      if (needPicklistValues(value?.left?.datatype)) {
        _fetchPicklistValues({
          dependantType: rightType,
          dependantId: value.left.value,
          params: {
            datatype: value.left?.datatype,
          },
        });
      }
    }
    if (value?.operator) {
      const opVal = operatorValues?.find((op: PicklistValue) => op.value === value.operator);
      opVal && setIsUnary(opVal.unary);
      setOperatorValue(opVal);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, operatorValues]);

  useEffect(() => {
    setValue(defaultValue);
  }, [defaultValue]);

  const onLhsSearchChange = (input: string) => {
    setSearchValue(input);
  };

  const onRightChange = useCallback(
    (right: RightValue) => {
      const newVal = {
        ...value,
        right,
      };

      setValue(newVal);
      onChange?.(newVal);
    },
    [onChange, value]
  );

  let showTokens = canShowToken(value?.left);

  const rightValueComponent = useMemo(() => {
    // Skip the right value if:
    // 1.) Its unary.
    // 3.) The operator object value is not found on a selected operator

    if (isUnary || (value?.operator && !operatorValue)) {
      return;
    }

    if (operatorValue?.datatype) {
      showTokens = isTokenSupportedDatatype(operatorValue.datatype);
    }

    return (
      <ConditionRightValue
        key={`condition-right-input-container-${name}`}
        defaultValue={value?.right}
        showTokens={showTokens}
        disabled={!Boolean(value?.left?.value && operatorValue) || allFieldsDisabled}
        leftValue={value?.left}
        name={name}
        displayMode={displayMode}
        onChange={onRightChange}
        operatorValue={operatorValue}
        value={value?.right}
        values={rightValues}
        rightOptions={rightOptions}
        placeHolder="Enter value"
      />
    );
  }, [
    displayMode,
    isUnary,
    name,
    onRightChange,
    operatorValue,
    rightValues,
    showTokens,
    value?.left,
    value?.operator,
    value?.right,
    placeHolder,
  ]);

  useEffect(() => {
    if (!isUndefined(value?.left?.value)) {
      let operatorVals = picklistValues[`${encodeURIComponent(value?.left?.value ?? '')}/${name}${operatorType}`];
      operatorVals && setOperatorValues(operatorVals as OperatorValue[]);
      if (needPicklistValues(value?.left?.datatype)) {
        let rightVals = picklistValues[`${encodeURIComponent(value?.left?.value ?? '')}/${name}${rightType}`];
        if (rightVals?.length) {
          setRightValues(rightVals);

          if (value?.right?.value) {
            const rightPick = rightVals.find((v: PicklistValue) => v.value === value.right?.value);
            if (rightPick?.label) {
              setRightDisplayValue(rightPick.label);
            } else if (value.right?.value) {
              setRightDisplayValue(String(value.right.value));
            }
            return;
          }
        }
      }
    }
    if (!isUndefined(value?.right?.value)) {
      setRightDisplayValue(
        typeof value?.right?.value === 'object' && !Array.isArray(value?.right?.value)
          ? rightValueComponent
          : String(value?.right?.value)
      );
    }
  }, [picklistValues, value, name, operatorType, rightType, rightValueComponent]);

  const _fetchPicklistValues = useCallback(
    (options: FetchPicklistParams) => {
      options.dependantId = encodeURIComponent(options.dependantId ?? '');
      const id = `${options.dependantId}/${name}${options.dependantType}`;

      if (picklistValues?.[id]) {
        return picklistValues[id];
      } else {
        fetchPicklistValues && fetchPicklistValues({ ...options, id });
      }
    },
    [fetchPicklistValues, name, picklistValues]
  );

  const onLeftChange = useCallback(
    (val: string | DataSourceFieldPickerValue) => {
      const left =
        typeof val === 'string'
          ? leftValues?.find((v: LeftValue) => v.value === val)
          : leftValues?.find((leftValue: LeftValue) =>
              val.datasourceAlias
                ? leftValue.id === val.id && leftValue.datasourceAlias === val.datasourceAlias
                : leftValue.id === val.id
            );

      const newVal = {
        ...value,
        left,
      };

      // TODO: are we duplicating state tracking? shouldn't
      // the onChange affect the incoming value instead of
      // tracking in 2 places?
      setValue(newVal);
      onChange?.({ ...newVal, name });
      _fetchPicklistValues({
        dependantId: left?.value,
        dependantType: operatorType,
        params: {
          datatype: newVal.left?.datatype,
        },
      });
      if (needPicklistValues(newVal.left?.datatype)) {
        _fetchPicklistValues({
          dependantId: left?.value,
          dependantType: rightType,
          params: {
            datatype: newVal.left?.datatype,
          },
        });
      }
    },
    [_fetchPicklistValues, leftValues, name, onChange, operatorType, rightType, value]
  );

  const onOperatorChange = (operator: string) => {
    const operatorObject = operatorValues?.find((op: PicklistValue) => op.value === operator);

    const newVal = {
      ...value,
      right: getValueForDatatypeAndOperator(value.right, value?.left?.datatype, operatorObject),
      operator,
    };

    setValue(newVal);
    onChange?.(newVal);
  };

  const dropdownRender = useMemo(() => {
    return allowUserToken
      ? (menu: React.ReactNode, props: any) => (
          <div>
            {menu}
            <div
              onMouseDown={() => {
                onLeftChange(searchValue);
              }}
              className={cx('user-token-condition-option', !isTokenInput && 'user-token-condition-option--disabled')}>
              {'Use entered Token Value'}
              <Tooltip
                title={
                  isTokenInput
                    ? `${searchValue}`
                    : // NOTE: there seems to be a bug with i18n when you substute in {{ and }} as string values. So we have to
                      // "pass" the token sentinels in this way instead of the built in way.
                      `${tn('token_value_help_1')}'${TOKEN_BEGIN_SENTINEL}'${tn(
                        'token_value_help_2'
                      )}'${TOKEN_END_SENTINEL}'.`
                }>
                <Icon type="info-circle" theme="filled" className="user-token-condition-option__icon" />
              </Tooltip>
            </div>
          </div>
        )
      : undefined;
  }, [allowUserToken, isTokenInput, onLeftChange, searchValue, tn]);

  return (
    <>
      {displayMode === READONLY ? (
        <ConditionReadOnly
          className={className}
          left={leftValues?.find((lval) => value?.left?.value === lval.value)?.label || value?.left?.label}
          operator={value?.operator}
          operatorLabel={operatorValue?.label}
          right={rightDisplayValue}
        />
      ) : (
        <HStack
          align="start"
          className={cx('synri-condition', className, {
            'data-studio-vertical-layout': verticalLayout,
            'data-studio-hide-left': hideLeftInput,
          })}>
          <div className="synri-condition-left" key={`condition-left-input-container-${name}`}>
            <FieldGroup label={showTokens && <HiddenLabel>{tn('condition_lhs')}</HiddenLabel>}>
              <InputContainer
                dropdownMatchSelectWidth={false}
                key={`condition-left-input-${name}`}
                aria-label={tn('condition_lhs')}
                disabled={lhsDisabled || allFieldsDisabled}
                useDataTypeBadges
                datatype={leftDatatype || AppConstants.INPUT_TYPE.PICKLIST}
                renderType={leftRenderType}
                values={leftValues}
                defaultValue={!value?.left?.datasourceAlias ? value?.left?.value : value?.left}
                onChange={onLeftChange}
                onSearchChange={allowUserToken ? onLhsSearchChange : undefined}
                isTokenInput={isTokenInput}
                dropdownRender={dropdownRender}
                placeholder={`${placeHolder ?? 'Select field'}`}
              />
            </FieldGroup>
          </div>
          <div className="synri-condition-operator" key={`condition-operator-input-container-${name}`}>
            <FieldGroup label={showTokens && <HiddenLabel>{tn('condition_operator')}</HiddenLabel>}>
              <InputContainer
                key={`condition-operator-input-${name}`}
                aria-label={tn('condition_operator')}
                datatype={AppConstants.INPUT_TYPE.PICKLIST}
                defaultValue={value?.operator}
                disabled={!value?.left?.value || allFieldsDisabled}
                values={operatorValues}
                onChange={onOperatorChange}
                placeholder="Select"
              />
            </FieldGroup>
          </div>
          {rightValueComponent}
        </HStack>
      )}
    </>
  );
};

export default withI18n(Condition, 'Filter');
