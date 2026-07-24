//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// Wrapper for the Condition so it can be used inside Filter
//
import { Button, Icon } from 'antd';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { useEffect, useMemo, useState } from 'react';
import { animated, useTransition } from 'react-spring';

import Condition, { ConditionProps } from 'components/inputs/condition';
import { ConditionValue, LeftValue, RightOption, PicklistValue, PicklistValues } from 'components/inputs/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import './FilterCondition.less';

const tnf = tNamespaced('Filter');

export interface FilterConditionValue extends ConditionValue {
  predicateId?: string;
}

export interface FilterConditionProps {
  name: string;
  defaultValue: FilterConditionValue;
  onAdd?: (predicateId: string) => void;
  onDelete?: (predicateId: string) => void;
  onChange?: (value: FilterConditionValue, name?: string, values?: any) => void;
  displayMode?: string;
  className?: string;
  fieldValues: LeftValue[];
  fetchPicklistValues: ConditionProps['fetchPicklistValues'];
  picklistValues: PicklistValues<PicklistValue[]>;
  operatorType?: string;
  rightType?: string;
  singleCondition?: boolean;
  leftRenderType?: string;
  lhsDisabled?: boolean;
  allowUserToken?: boolean;
  rightValues?: RightOption[];
}

const FilterCondition = ({
  name,
  defaultValue,
  onAdd,
  onDelete,
  onChange,
  displayMode,
  className,
  fieldValues,
  fetchPicklistValues,
  picklistValues,
  operatorType,
  rightType,
  singleCondition = false,
  leftRenderType,
  lhsDisabled,
  allowUserToken,
  rightValues,
}: FilterConditionProps) => {
  const [predicateId] = useState(defaultValue?.predicateId || ObjectID.generate());
  const [value, setValue] = useState(defaultValue);
  const [active, setActive] = useState(false);

  useEffect(() => {
    setValue(defaultValue);
  }, [defaultValue]);

  const transition = useTransition(active, {
    initial: { maxHeight: 40, opacity: 1 },
    leave: { maxHeight: 0, opacity: 0 },
    from: { maxHeight: 0, opacity: 0 },
    enter: { maxHeight: 40, opacity: 1 },
  });

  const filter = useMemo(() => {
    if (!predicateId) {
      return <div />;
    }
    const isReadonly = displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;
    return (
      <div
        className={cx(
          'synri-filter-condition',
          {
            active: active && !isReadonly && !singleCondition,
            'with-textarea': value?.left?.datatype === AppConstants.INPUT_TYPE.TEXTAREA,
            'synri-filter-condition-readonly': isReadonly,
            'single-condition': singleCondition,
          },
          className
        )}
        onMouseEnter={() => setActive(true)}
        onMouseLeave={() => setActive(false)}>
        <div key={predicateId} className="filter-condition-container">
          <div className="filter-input-container">
            <Condition
              name={name}
              lhsDisabled={lhsDisabled}
              values={fieldValues}
              defaultValue={value}
              fetchPicklistValues={fetchPicklistValues}
              picklistValues={picklistValues}
              displayMode={displayMode}
              onChange={(newVal: ConditionValue) => onChange?.(newVal)}
              operatorType={operatorType}
              rightType={rightType}
              leftRenderType={leftRenderType}
              allowUserToken={allowUserToken}
              rightValues={rightValues}
            />
          </div>
          {!isReadonly && !singleCondition && (
            <Icon onClick={() => onDelete && onDelete(predicateId)} type="delete" theme="filled" />
          )}
        </div>
        {onAdd &&
          !isReadonly &&
          !singleCondition &&
          transition(
            (props, item) =>
              item && (
                <animated.div style={props}>
                  <Button
                    type="link"
                    className="btn-add-condition btn-single-filter"
                    onClick={() => onAdd(predicateId)}>
                    {tnf('add_condition')}
                  </Button>
                </animated.div>
              )
          )}
      </div>
    );
  }, [
    predicateId,
    displayMode,
    active,
    singleCondition,
    allowUserToken,
    value,
    className,
    name,
    lhsDisabled,
    fieldValues,
    fetchPicklistValues,
    picklistValues,
    operatorType,
    rightType,
    leftRenderType,
    onAdd,
    transition,
    onChange,
    onDelete,
  ]);

  return filter && Array.isArray(filter) ? <div className="filter-container">{filter}</div> : filter;
};

export default FilterCondition;
