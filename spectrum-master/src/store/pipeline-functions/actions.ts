//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { PromiseThunkAction } from 'store/types';
import { get } from 'utils/AjaxUtil';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { ArcadePipelineFunction } from './types';
import { getResponsePipelineFunctions } from './utils';

export const ActionTypes = {
  GET_FIELD_PIPELINE_FUNCTIONS: 'GET_FIELD_PIPELINE_FUNCTIONS',
  GET_FIELD_PIPELINE_FUNCTIONS_PENDING: 'GET_FIELD_PIPELINE_FUNCTIONS_PENDING',
  GET_FIELD_PIPELINE_FUNCTIONS_FULFILLED: 'GET_FIELD_PIPELINE_FUNCTIONS_FULFILLED',
  GET_FIELD_PIPELINE_FUNCTIONS_FAILED: 'GET_FIELD_PIPELINE_FUNCTIONS_FAILED',

  GET_ENTITY_PIPELINE_FUNCTIONS: 'GET_ENTITY_PIPELINE_FUNCTIONS',
  GET_ENTITY_PIPELINE_FUNCTIONS_PENDING: 'GET_ENTITY_PIPELINE_FUNCTIONS_PENDING',
  GET_ENTITY_PIPELINE_FUNCTIONS_FULFILLED: 'GET_ENTITY_PIPELINE_FUNCTIONS_FULFILLED',
  GET_ENTITY_PIPELINE_FUNCTIONS_FAILED: 'GET_ENTITY_PIPELINE_FUNCTIONS_FAILED',

  CLEAR_FIELD_PIPELINE_FUNCTIONS: 'CLEAR_FIELD_PIPELINE_FUNCTIONS',
  CLEAR_ENTITY_PIPELINE_FUNCTIONS: 'CLEAR_ENTITY_PIPELINE_FUNCTIONS',
} as const;

/**
 * Get the list of field pipeline functions
 */
export function getEntityPipelineFunctions(entityId: string): PromiseThunkAction {
  return async (dispatch) => {
    dispatch({
      type: ActionTypes.GET_ENTITY_PIPELINE_FUNCTIONS_PENDING,
    });

    try {
      const resp = await get<ArcadePipelineFunction[]>(makeUrl(DataUrlConstants.ENTITY_FUNCTIONS, { entityId }));
      dispatch({
        type: ActionTypes.GET_ENTITY_PIPELINE_FUNCTIONS_FULFILLED,
        payload: getResponsePipelineFunctions(resp.data),
      });
    } catch (error) {
      dispatch({
        type: ActionTypes.GET_ENTITY_PIPELINE_FUNCTIONS_FAILED,
        error: getErrorMessage(error),
      });
    }
  };
}

/**
 * Get the list of field pipeline functions
 */
export function getFieldPipelineFunctions(graphId: string): PromiseThunkAction {
  return async (dispatch) => {
    dispatch({
      type: ActionTypes.GET_FIELD_PIPELINE_FUNCTIONS_PENDING,
    });

    try {
      const resp = await get<ArcadePipelineFunction[]>(makeUrl(DataUrlConstants.FIELD_FUNCTIONS, { graphId }));
      dispatch({
        type: ActionTypes.GET_FIELD_PIPELINE_FUNCTIONS_FULFILLED,
        payload: getResponsePipelineFunctions(resp.data),
      });
    } catch (error) {
      dispatch({
        type: ActionTypes.GET_FIELD_PIPELINE_FUNCTIONS_FAILED,
        error: getErrorMessage(error),
      });
    }
  };
}

// TODO: This doesn't appear to be used anywhere, we can probably remove it
export function clearEntityPipelineFunctions() {
  return {
    type: ActionTypes.CLEAR_ENTITY_PIPELINE_FUNCTIONS,
  };
}

// TODO: This doesn't appear to be used anywhere, we can probably remove it
export function clearFieldPipelineFunctions() {
  return {
    type: ActionTypes.CLEAR_FIELD_PIPELINE_FUNCTIONS,
  };
}
