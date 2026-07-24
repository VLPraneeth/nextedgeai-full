/* eslint-disable jsx-a11y/anchor-is-valid */
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Dropdown, Icon, Menu } from 'antd';
import { ClickParam } from 'antd/lib/menu';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { cloneDeep, delay, each, first, map, remove } from 'lodash';
import * as React from 'react';
import { forwardRef, ReactElement, useEffect, useImperativeHandle, useState } from 'react';

import {
  ConditionValue,
  FilterValue,
  isConditionValue,
  LeftValue,
  RightOption,
  PicklistValue,
  PicklistValues,
} from 'components/inputs/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import FilterCondition, { FilterConditionProps, FilterConditionValue } from './FilterCondition';

import './index.less';

export const OPERATOR = {
  AND: 'AND',
  OR: 'OR',
};

// Max filter condition level the backend support
const MAX_LEVEL = 3;

const tn = tNamespaced('Filter');

/* simple validation to determine if this is an empty predicate */
const predicateIsEmpty = (predicate: ConditionValue) => {
  if (isConditionValue(predicate)) {
    return [predicate.left, predicate.operator, predicate.right].every((value) => typeof value === 'undefined');
  }

  // not a predicate?
  return false;
};

const _getNewPredicate = (): ConditionValue => {
  return {
    left: undefined,
    operator: undefined,
    right: undefined,
    predicateId: ObjectID.generate(),
  };
};

const makePredicateFromFilterCondition = (condition: ConditionValue = {}) => {
  return {
    ..._getNewPredicate(),
    ...condition,
  };
};

const _getNewGroup = (predicates?: FilterValue['predicates']) => {
  predicates = predicates || [_getNewPredicate()];
  return {
    predicates,
    groupPredicateId: ObjectID.generate(),
    operator: OPERATOR.AND,
  };
};

export type ExternalOnChangeHandler = (name: string, key: string, value: FilterValue) => void; // External onChange
export type InternalOnChangeHandler = (value: FilterConditionValue, _name?: string, filterValue?: FilterValue) => void; // Recursive onChange

export interface BaseFilterProps {
  /**
   * Name of the filter. Name be on value of change event
   */
  name: string;
  /**
   * Display mode
   */
  displayMode?: string;
  /**
   * Value of the filter
   */
  value?: Partial<FilterValue>;
  /**
   * Similar functionality with value
   */
  defaultValue?: FilterValue;
  /**
   * Internal: Filter can be nested and this is the level it is in. This is
   * used internally and should not be set from outside of the component
   */
  level?: number;
  /**
   * className that will be added to the component
   */
  className?: string;
  /**
   * Handler when a picklist values is needed to be fetched
   */
  fetchPicklistValues: FilterConditionProps['fetchPicklistValues'];
  /**
   * List of picklist values that the picklist inputs in the filter will pull their respective picklist values
   */
  picklistValues: PicklistValues<PicklistValue[]>;
  /**
   * Picklist value that will be used for the first picklist (AKA Field picklist)
   */
  fieldValues: LeftValue[];
  /**
   * Use this a dependantType for getting the picklist values of the operator value. 'Operator' by default.
   */
  operatorType?: string;
  /**
   * Use this a dependantType for getting the picklist values of the right value. 'Right' by default.
   */
  rightType?: string;
  /**
   * Hides the add filter and delete button when turned on
   */
  singleCondition?: boolean;
  /**
   * Render type of the left hand side input
   */
  leftRenderType?: string;
  lhsDisabled?: boolean;
  allowUserToken?: boolean;
  rightValues?: RightOption[];
  isAllDisabled?: boolean;
}

export interface FilterProps extends BaseFilterProps {
  /**
   * Use the external onChange: (name: string, key: string, value: FilterValue) => void)
   * TODO: Separate the external and internal recursive onChange.
   *       Refactor to add another layer so that the recursive props will not be exposed.
   */
  onChange: ExternalOnChangeHandler;
  /**
   * Internal: called when a condition is deleted
   */
  onDelete?: undefined;
}

export interface InternalFilterProps extends BaseFilterProps {
  /**
   * Use the external onChange: (name: string, key: string, value: FilterValue) => void)
   * TODO: Separate the external and internal recursive onChange.
   *       Refactor to add another layer so that the recursive props will not be exposed.
   */
  onChange: InternalOnChangeHandler;
  /**
   * Internal: called when a condition is deleted
   */
  onDelete: (predicateId?: string, groupPredicateId?: string) => void;
}

export interface FilterRef {
  addFilterCondition: (filterCondition: ConditionValue, replaceIfEmpty: boolean) => void;
}

const Filter = forwardRef<FilterRef, FilterProps | InternalFilterProps>(
  (
    {
      name,
      displayMode,
      value,
      defaultValue,
      level = 1,
      onChange,
      onDelete,
      className,
      picklistValues,
      fetchPicklistValues,
      operatorType,
      rightType,
      fieldValues,
      singleCondition,
      leftRenderType,
      lhsDisabled,
      allowUserToken,
      rightValues,
    },
    ref
  ) => {
    const initializePredicates = (val: FilterProps['value'], defaultVal: FilterProps['defaultValue']) => {
      let initialValue;

      if (val) {
        initialValue = val;
      } else if (defaultVal) {
        initialValue = defaultVal;
      } else {
        initialValue = _getNewGroup(undefined);
      }

      return initialValue;
    };

    // TODO: We are mixing predicates, keep this any for now. Refactor this later...
    const [predicates, setPredicates] = useState<any>(() => initializePredicates(value, defaultValue));
    const [operator, setOperator] = useState(() => predicates?.operator || OPERATOR.AND);

    const isReadonly = displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY;

    useEffect(() => {
      if (!value || !defaultValue) {
        const newPredicates = initializePredicates(value, defaultValue);
        setPredicates(newPredicates);
        setOperator(newPredicates?.operator || OPERATOR.AND);
      }
    }, [value, defaultValue]);

    const _getAddGroupCondition = () => {
      if (!isReadonly) {
        return (
          <div className="btn-filter-add-group">
            <Button type="link" className="btn-add-condition btn-group-filter" onClick={_onAdd}>
              {tn('add_condition')}
            </Button>
          </div>
        );
      }
    };

    const _onChange = (value: FilterConditionValue, _name?: string, filterValue?: FilterValue) => {
      let newValue = true;
      let _predicates;
      // From a filter or from a group of filters. String if from a filter
      let changeValue: FilterConditionValue | FilterValue = filterValue?.groupPredicateId ? filterValue : value;

      if (value || filterValue) {
        // bug on the onchange of the new group that duplicates that values of the filters in the group
        _predicates = map(predicates?.predicates, (fVal) => {
          if (filterValue?.groupPredicateId && fVal.groupPredicateId === filterValue.groupPredicateId) {
            newValue = false;
            return cloneDeep(filterValue);
          }
          if (value?.predicateId && fVal.predicateId === value.predicateId) {
            newValue = false;
            return cloneDeep(value);
          }
          return fVal;
        });
        if (newValue) {
          _predicates.push(changeValue);
        }
      } else {
        _predicates = predicates.predicates;
      }

      const values = {
        predicates: cloneDeep(_predicates),
        groupPredicateId: predicates?.groupPredicateId,
        operator,
      };
      setPredicates(values);

      // TODO: Need to process group filters
      if (onChange) {
        onChange(name, name, cloneDeep(values));
      }
    };

    const onGroupOperatorChange = (event: ClickParam) => {
      setOperator(event.key);
      if (onChange) {
        onChange(
          name,
          name,
          cloneDeep({
            ...predicates,
            operator: event.key,
          })
        );
      }
    };

    const _onAdd: any = (
      predicateId?: string | React.MouseEvent<HTMLButtonElement>,
      filterCondition?: ConditionValue,
      replaceIfEmpty?: boolean
    ) => {
      const newPredicate = makePredicateFromFilterCondition(filterCondition);

      // TODO: document these branches
      if (predicates?.predicates?.length === 1) {
        let newPredicates = [];
        // Simple add on the first level if its the only one
        if (level === 1) {
          // if we've provided a new filterCondition and there's only 1 predicate,
          // we might want to replace the empty predicate with our provided condition
          newPredicates =
            replaceIfEmpty && predicateIsEmpty(predicates.predicates[0])
              ? [newPredicate]
              : predicates.predicates.concat(newPredicate);
        } else if (level > 1) {
          each(predicates?.predicates, (predicate) => {
            if (predicate.predicateId === predicateId) {
              newPredicates.push(_getNewGroup([predicate, newPredicate]));
            } else {
              newPredicates.push(predicate);
            }
          });
        }

        const updatedPredicates = {
          ...predicates,
          predicates: newPredicates,
        };

        setPredicates(updatedPredicates);
        onChange(name, name, updatedPredicates);
      } else if (predicates?.predicates?.length > 1) {
        // TODO: document this branch
        let addedNew = false;
        const newPredicates = [];
        each(predicates?.predicates, (predicate) => {
          if (predicate.predicateId === predicateId) {
            addedNew = true;
            newPredicates.push(_getNewGroup([predicate, newPredicate]));
          } else {
            newPredicates.push(predicate);
          }
        });

        if (!addedNew) {
          newPredicates.push(newPredicate);
        }

        const updatedPredicates = {
          ...predicates,
          predicates: newPredicates,
        };

        setPredicates(updatedPredicates);
        onChange(name, name, updatedPredicates);
      }
    };

    const _onDelete = (predicateId?: string, groupPredicateId?: string) => {
      const _predicates = cloneDeep(predicates?.predicates);

      if (predicateId) {
        remove(_predicates, (predicate: FilterConditionValue) => {
          return predicate.predicateId === predicateId;
        });
      }

      if (groupPredicateId) {
        remove(_predicates, (predicate: FilterValue) => {
          return predicate.groupPredicateId === groupPredicateId;
        });
      }

      // Only prefill an empty condition when its at the root level.
      // Remove any empty grouped predicate
      if (_predicates?.length <= 0 && level <= 1) {
        _predicates.push(_getNewPredicate());
      }
      const newPredicates = cloneDeep({
        ...predicates,
        predicates: _predicates,
      });

      setPredicates(newPredicates);

      const _groupPredicateId = predicates.groupPredicateId;

      // TODO: remove delay? Maybe use an effect instead
      delay(() => {
        if (onChange) {
          onChange(name, name, newPredicates);
        }
        if (_predicates.length <= 0 && onDelete) {
          onDelete(undefined, _groupPredicateId);
        }
      }, 10);
    };

    const _getGroupOperator = () => {
      if (isReadonly) {
        return;
      }

      const menu = (
        <Menu onClick={onGroupOperatorChange}>
          <Menu.Item key="AND">
            <a>{tn('and')}</a>
          </Menu.Item>
          <Menu.Item key="OR">
            <a>{tn('or')}</a>
          </Menu.Item>
        </Menu>
      );
      return (
        <div className="filter-operator-container">
          <Dropdown overlay={menu} trigger={['click']}>
            <a className="ant-dropdown-link" href="#">
              {operator} <Icon type="down" />
            </a>
          </Dropdown>
        </div>
      );
    };

    // Access these methods via ref
    useImperativeHandle(ref, () => ({
      addFilterCondition: (filterCondition: ConditionValue, replaceIfEmpty: boolean) =>
        _onAdd(undefined, filterCondition, replaceIfEmpty),
    }));

    // TODO: Not ideal, performace trashing. Refactor this
    const cls = cx('synri-filter-condition-group', className);
    const filters: ReactElement[] = [];

    if (predicates?.predicates?.length <= 1) {
      const predicate: any = first(predicates?.predicates);

      if (predicate?.groupPredicateId) {
        return (
          <Filter
            name={name}
            key={`filter-${predicate?.groupPredicateId}`}
            displayMode={displayMode}
            onChange={_onChange}
            onDelete={_onDelete}
            leftRenderType={leftRenderType}
            lhsDisabled={lhsDisabled}
            value={predicate}
            level={level + 1}
            fieldValues={fieldValues}
            fetchPicklistValues={fetchPicklistValues}
            picklistValues={picklistValues}
            operatorType={operatorType}
            rightType={rightType}
            allowUserToken={allowUserToken}
            rightValues={rightValues}
          />
        );
      }
      return (
        <div className={cls} key={`filter-condition-container-${predicate?.predicateId}`}>
          <FilterCondition
            name={name}
            onAdd={_onAdd}
            defaultValue={predicate}
            displayMode={displayMode}
            onChange={_onChange}
            onDelete={_onDelete}
            lhsDisabled={lhsDisabled}
            leftRenderType={leftRenderType}
            fetchPicklistValues={fetchPicklistValues}
            picklistValues={picklistValues}
            fieldValues={fieldValues}
            operatorType={operatorType}
            rightType={rightType}
            singleCondition={singleCondition}
            allowUserToken={allowUserToken}
            rightValues={rightValues}
          />
        </div>
      );
    } else if (predicates?.predicates?.length > 1) {
      // Show the group elements for multiple filters
      each(predicates?.predicates, (predicate) => {
        const itemKey = predicate.predicateId || predicate.groupPredicateId;
        const localPredicate = predicate;

        if (filters.length > 0 && isReadonly) {
          filters.push(
            <div key={`readonly-${itemKey}`} className="synri-readonly-operator">
              {operator}
            </div>
          );
        }

        if (predicate?.groupPredicateId) {
          filters.push(
            <Filter
              name={name}
              key={`filter-${predicate?.groupPredicateId}`}
              displayMode={displayMode}
              onChange={_onChange}
              onDelete={_onDelete}
              lhsDisabled={lhsDisabled}
              leftRenderType={leftRenderType}
              value={localPredicate}
              level={level + 1}
              fetchPicklistValues={fetchPicklistValues}
              picklistValues={picklistValues}
              fieldValues={fieldValues}
              operatorType={operatorType}
              rightType={rightType}
              allowUserToken={allowUserToken}
              rightValues={rightValues}
            />
          );
        } else {
          filters.push(
            <div className={cls} key={`filter-condition-container-${localPredicate.predicateId}`}>
              <FilterCondition
                name={name}
                onAdd={level < MAX_LEVEL && _onAdd}
                defaultValue={localPredicate}
                displayMode={displayMode}
                onChange={_onChange}
                onDelete={_onDelete}
                lhsDisabled={lhsDisabled}
                leftRenderType={leftRenderType}
                fetchPicklistValues={fetchPicklistValues}
                picklistValues={picklistValues}
                fieldValues={fieldValues}
                operatorType={operatorType}
                rightType={rightType}
                singleCondition={singleCondition}
                allowUserToken={allowUserToken}
                rightValues={rightValues}
              />
            </div>
          );
        }
      });
      const groupOperator = _getGroupOperator();
      let addFilterGroupBtn = _getAddGroupCondition();
      const groupCls = cx('group-filter-container', {
        'root-group': level === 1,
        'synri-filter-readonly': isReadonly,
      });
      return (
        <div className={groupCls}>
          {groupOperator}
          <div className="filter-group-groups">
            {filters}
            {addFilterGroupBtn}
          </div>
        </div>
      );
    }
    return <div />;
  }
);

export default Filter;
