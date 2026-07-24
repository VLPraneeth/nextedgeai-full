//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import {
  GET_PICKLIST_VALUES,
  GET_PICKLIST_VALUES_PENDING,
  GET_PICKLIST_VALUES_FULFILLED,
  GET_PICKLIST_VALUES_FAILED,
  FETCH_PICKLIST_VALUES_PENDING,
  FETCH_PICKLIST_VALUES_FULFILLED,
  FETCH_PICKLIST_VALUES_FAILED,
} from 'store/picklists/types';

export { getPicklistValues, fetchPicklistValues } from 'store/picklists/thunks';
export { invalidatePicklistValues } from 'store/picklists/actions';

export const ActionTypes = {
  GET_PICKLIST_VALUES,
  GET_PICKLIST_VALUES_PENDING,
  GET_PICKLIST_VALUES_FULFILLED,
  GET_PICKLIST_VALUES_FAILED,
  FETCH_PICKLIST_VALUES_PENDING,
  FETCH_PICKLIST_VALUES_FULFILLED,
  FETCH_PICKLIST_VALUES_FAILED,
};
