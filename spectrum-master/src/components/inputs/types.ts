//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { SkullItem } from 'components/skull';
import { FieldDataType } from 'components/types';
import AppConstants from 'utils/AppConstants';
import { ValuesOf } from 'utils/TypeUtils';

export type InputDataType = ValuesOf<typeof AppConstants.INPUT_TYPE>;
export type InputRenderType = ValuesOf<typeof AppConstants.INPUT_RENDER_TYPE>;

/**
 * Ant validate status (ValidateStatus)
 */
export enum ValidateStatuses {
  SUCCESS = 'success',
  WARNING = 'warning',
  ERROR = 'error',
  VALIDATING = 'validating',
  BLANK = '',
}

export type ValidateStatus = typeof ValidateStatuses;

// TODO: Need a better structure for the validation type
// This will cause typeof checks in code :(
export type Validation = Record<string, ValidateStatus | string | undefined>;

export interface SelectOption {
  title?: string;
  value: string;
  id?: string;
}

export type SelectOptions = SelectOption[];

export interface PicklistValue extends SelectOption {
  label: string;
  picklistGroup?: string;
  dataType?: FieldDataType;
}

export interface PicklistValueServer extends Omit<PicklistValue, 'dataType'> {
  datatype?: FieldDataType;
}

export type PicklistValues<T> = Record<string, T>;

export interface RightValue {
  value?: string | boolean | string[];
  type: 'literal';
}

export interface RightOption {
  id?: string;
  value?: string;
  datatype?: InputDataType;
  label?: string;
  renderType?: InputRenderType;
  picklistGroup?: string;
  type?: string;
  datasourceAlias?: string;
}

export interface LeftValue {
  id?: string;
  value?: string;
  datatype?: InputDataType;
  label?: string;
  renderType?: InputRenderType;
  picklistGroup?: string;
  type?: string;
  datasourceAlias?: string;
}

export type DisplayMode = ValuesOf<typeof AppConstants.INPUT_DISPLAY_MODE>;

export interface OperatorValue extends PicklistValue {
  unary: boolean;
  datatype?: InputDataType;
  renderType?: InputRenderType;
  configuration?: SkullItem[];
}

// typeguard
export const isConditionValue = (variableToCheck: any): variableToCheck is ConditionValue => {
  return (
    variableToCheck &&
    ['predicateId', 'left', 'operator', 'right'].some((key) => typeof variableToCheck[key] !== 'undefined')
  );
};

export interface ConditionValue {
  predicateId?: string;
  name?: string;
  left?: LeftValue;
  operator?: string;
  right?: RightValue;
}

export interface FilterValue {
  groupPredicateId: string;
  operator: string;
  predicates: (FilterValue | ConditionValue)[];
}

export const isGroupPredicate = (variableToCheck: any): variableToCheck is FilterValue => {
  return variableToCheck && typeof variableToCheck.groupPredicateId !== 'undefined';
};
