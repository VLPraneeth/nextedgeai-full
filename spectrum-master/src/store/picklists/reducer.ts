//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import produce, { Draft } from 'immer';

import AppConstants from 'utils/AppConstants';

import {
  PicklistsState,
  GET_PICKLIST_VALUES_PENDING,
  GET_PICKLIST_VALUES_FULFILLED,
  GET_PICKLIST_VALUES_FAILED,
  FETCH_PICKLIST_VALUES_PENDING,
  FETCH_PICKLIST_VALUES_FULFILLED,
  INVALIDATE_PICKLIST_VALUES,
} from './types';

const { FETCH_STATUS } = AppConstants;

export function _getDefaultState() {
  return {
    fetchingPicklistValues: false,
    picklistValues: {},
    fetchingPicklistValuesStatus: {},
  };
}

const reducer = produce((draft: Draft<PicklistsState>, action) => {
  switch (action.type) {
    case GET_PICKLIST_VALUES_PENDING:
      draft.fetchingPicklistValues = true;
      break;
    case GET_PICKLIST_VALUES_FULFILLED:
      draft.fetchingPicklistValues = false;
      draft[action.key] = action.pickListValues;
      break;
    case GET_PICKLIST_VALUES_FAILED:
      draft.fetchingPicklistValues = false;
      break;
    case FETCH_PICKLIST_VALUES_PENDING:
      draft.fetchingPicklistValuesStatus[action.id] = FETCH_STATUS.LOADING;
      break;
    case FETCH_PICKLIST_VALUES_FULFILLED: {
      const { id, pickListValues } = action;
      draft.fetchingPicklistValuesStatus[id] = FETCH_STATUS.SUCCESS;
      draft.picklistValues[id] = pickListValues;
      break;
    }
    case INVALIDATE_PICKLIST_VALUES:
      draft.picklistValues = {};
      draft.fetchingPicklistValuesStatus = {};
      break;
    default:
      return draft;
  }
}, _getDefaultState());

export default reducer;
