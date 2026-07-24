// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import AppConstants from 'utils/AppConstants';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';

import { ActionTypes } from './actions';

function _getDefaultState() {
  return {
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.PIPELINE_ACTION),
    fieldPipelineActions: [],
    fieldPipelineActionsError: undefined,
    fieldPipelineActionsFetching: false,
    entityPipelineActions: [],
    entityPipelineActionsError: undefined,
    entityPipelineActionsFetching: false,
  };
}

export default function pipelineFunctionReducer(state = _getDefaultState(), action) {
  switch (action.type) {
    case ActionTypes.GET_FIELD_PIPELINE_ACTIONS_PENDING:
      return {
        ...state,
        fieldPipelineActionsError: undefined,
        fieldPipelineActionsFetching: true,
      };
    case ActionTypes.GET_FIELD_PIPELINE_ACTIONS_FULFILLED:
      return {
        ...state,
        fieldPipelineActions: action.payload,
        fieldPipelineActionsError: undefined,
        fieldPipelineActionsFetching: false,
      };
    case ActionTypes.GET_FIELD_PIPELINE_ACTIONS_FAILED:
      return {
        ...state,
        fieldPipelineActions: [],
        fieldPipelineActionsError: action.error,
        fieldPipelineActionsFetching: false,
      };
    case ActionTypes.CLEAR_FIELD_PIPELINE_ACTIONS:
      return {
        ...state,
        fieldPipelineActions: [],
        fieldPipelineActionsError: undefined,
      };
    case ActionTypes.GET_ENTITY_PIPELINE_ACTIONS_PENDING:
      return {
        ...state,
        entityPipelineActionsError: undefined,
        entityPipelineActionsFetching: true,
      };
    case ActionTypes.GET_ENTITY_PIPELINE_ACTIONS_FULFILLED:
      return {
        ...state,
        entityPipelineActions: action.payload,
        entityPipelineActionsError: undefined,
        entityPipelineActionsFetching: false,
      };
    case ActionTypes.GET_ENTITY_PIPELINE_ACTIONS_FAILED:
      return {
        ...state,
        entityPipelineActions: [],
        entityPipelineActionsError: action.error,
        entityPipelineActionsFetching: false,
      };
    case ActionTypes.CLEAR_ENTITY_PIPELINE_ACTIONS:
      return {
        ...state,
        entityPipelineActions: [],
        entityPipelineActionsError: undefined,
      };
    default:
      return state;
  }
}
