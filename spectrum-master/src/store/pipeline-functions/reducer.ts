// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import AppConstants from 'utils/AppConstants';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';

import { ActionTypes } from './actions';

function _getDefaultState() {
  return {
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.PIPELINE_FUNCTION),
    entityPipelineFunctions: [],
    entityPipelineFunctionsError: undefined,
    entityPipelineFunctionsFetching: false,
    fieldPipelineFunctions: [],
    fieldPipelineFunctionsError: undefined,
    fieldPipelineFunctionsFetching: false,
  };
}

export default function pipelineFunctionReducer(state = _getDefaultState(), action) {
  switch (action.type) {
    case ActionTypes.GET_ENTITY_PIPELINE_FUNCTIONS_PENDING:
      return {
        ...state,
        entityPipelineFunctionsError: undefined,
        entityPipelineFunctionsFetching: true,
      };
    case ActionTypes.GET_ENTITY_PIPELINE_FUNCTIONS_FULFILLED:
      return {
        ...state,
        entityPipelineFunctions: action.payload,
        entityPipelineFunctionsError: undefined,
        entityPipelineFunctionsFetching: false,
      };
    case ActionTypes.GET_ENTITY_PIPELINE_FUNCTIONS_FAILED:
      return {
        ...state,
        entityPipelineFunctions: [],
        entityPipelineFunctionsError: action.error?.errorMessage,
        entityPipelineFunctionsFetching: false,
      };
    case ActionTypes.CLEAR_ENTITY_PIPELINE_FUNCTIONS:
      return {
        ...state,
        entityPipelineFunctions: [],
        entityPipelineFunctionsError: undefined,
      };
    case ActionTypes.GET_FIELD_PIPELINE_FUNCTIONS_PENDING:
      return {
        ...state,
        fieldPipelineFunctionsError: undefined,
        fieldPipelineFunctionsFetching: true,
      };
    case ActionTypes.GET_FIELD_PIPELINE_FUNCTIONS_FULFILLED:
      return {
        ...state,
        fieldPipelineFunctions: action.payload,
        fieldPipelineFunctionsError: undefined,
        fieldPipelineFunctionsFetching: false,
      };
    case ActionTypes.GET_FIELD_PIPELINE_FUNCTIONS_FAILED:
      return {
        ...state,
        fieldPipelineFunctions: [],
        fieldPipelineFunctionsError: action.error?.errorMessage,
        fieldPipelineFunctionsFetching: false,
      };
    case ActionTypes.CLEAR_FIELD_PIPELINE_FUNCTIONS:
      return {
        ...state,
        fieldPipelineFunctions: [],
        fieldPipelineFunctionsError: undefined,
      };
    default:
      return state;
  }
}
