//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { cleanupFilterInputValue } from 'components/inputs/filter/utils';
import { FilterValue, LeftValue, OperatorValue } from 'components/inputs/types';
import { SkullInput } from 'components/skull';
import { EMPTY_ARRAY } from 'store/constants';

import AppConstants from './AppConstants';

const CHECKBOX_DATA_TYPES: String[] = [AppConstants.INPUT_TYPE.BOOLEAN, AppConstants.INPUT_TYPE.CHECKBOX];

/**
 * Normalize the values thats coming from the backend for different datatypes
 */
export function getDefaultValue(value: any) {
  return value?.value ?? value;
}

/**
 * Normalize the values to save to the backend
 */
export function getSaveValue(value: any) {
  return value?.value ?? value;
}

export const getDefaultRHSValue = (leftValue?: LeftValue, operatorValue?: OperatorValue) => {
  if (operatorValue?.datatype === AppConstants.INPUT_TYPE.MULTIVALUETEXT) {
    return EMPTY_ARRAY;
  }

  if (leftValue?.datatype === AppConstants.INPUT_TYPE.BOOLEAN) {
    return false;
  }

  return '';
};

/**
 * Any post input value cleanups before the input value gets sent to the server
 */
export const cleanupInputValue = (config: SkullInput, value: unknown) => {
  switch (config.datatype) {
    case AppConstants.INPUT_TYPE.PREDICATE:
      return cleanupFilterInputValue(value as FilterValue);
  }
  return value;
};

export const getPreferredDatatype = (dataType: string) => {
  // Override to checkbox since boolean are now expected to render checkboxes.
  return CHECKBOX_DATA_TYPES.includes(dataType) ? AppConstants.INPUT_TYPE.CHECKBOX : dataType;
};

// Data types that need to get value from checkecd property of change event
export const isCheckedValueDatatype = (dataType: string) => CHECKBOX_DATA_TYPES.includes(dataType);
