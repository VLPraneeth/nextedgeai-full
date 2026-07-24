//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { PromiseThunkAction } from 'store/types';
import { get } from 'utils/AjaxUtil';
import { getApplicationError, handleAsApplicationError } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { PipelineAction } from './types';
import { getResponsePipelineActions } from './utils';

export const ActionTypes = {
  GET_FIELD_PIPELINE_ACTIONS: 'GET_FIELD_PIPELINE_ACTIONS',
  GET_FIELD_PIPELINE_ACTIONS_PENDING: 'GET_FIELD_PIPELINE_ACTIONS_PENDING',
  GET_FIELD_PIPELINE_ACTIONS_FULFILLED: 'GET_FIELD_PIPELINE_ACTIONS_FULFILLED',
  GET_FIELD_PIPELINE_ACTIONS_FAILED: 'GET_FIELD_PIPELINE_ACTIONS_FAILED',

  GET_ENTITY_PIPELINE_ACTIONS: 'GET_ENTITY_PIPELINE_ACTIONS',
  GET_ENTITY_PIPELINE_ACTIONS_PENDING: 'GET_ENTITY_PIPELINE_ACTIONS_PENDING',
  GET_ENTITY_PIPELINE_ACTIONS_FULFILLED: 'GET_ENTITY_PIPELINE_ACTIONS_FULFILLED',
  GET_ENTITY_PIPELINE_ACTIONS_FAILED: 'GET_ENTITY_PIPELINE_ACTIONS_FAILED',

  CLEAR_FIELD_PIPELINE_ACTIONS: 'CLEAR_FIELD_PIPELINE_ACTIONS',
  CLEAR_ENTITY_PIPELINE_ACTIONS: 'CLEAR_ENTITY_PIPELINE_ACTIONS',
} as const;

export function getFieldPipelineActions(fieldId: string): PromiseThunkAction {
  return async (dispatch) => {
    dispatch({
      type: ActionTypes.GET_FIELD_PIPELINE_ACTIONS_PENDING,
    });

    try {
      const resp = await get<PipelineAction[]>(makeUrl(DataUrlConstants.FIELD_ACTIONS, { fieldId }));

      dispatch({
        type: ActionTypes.GET_FIELD_PIPELINE_ACTIONS_FULFILLED,
        payload: getResponsePipelineActions(resp.data),
      });
    } catch (error) {
      dispatch({
        type: ActionTypes.GET_FIELD_PIPELINE_ACTIONS_FAILED,
        error: getApplicationError(error),
      });

      handleAsApplicationError(dispatch)(error);
    }
  };
}

export function getEntityPipelineActions(entityId: string): PromiseThunkAction {
  return async (dispatch) => {
    dispatch({
      type: ActionTypes.GET_ENTITY_PIPELINE_ACTIONS_PENDING,
    });

    try {
      const resp = await get<PipelineAction[]>(makeUrl(DataUrlConstants.ENTITY_ACTIONS, { entityId }));

      dispatch({
        type: ActionTypes.GET_ENTITY_PIPELINE_ACTIONS_FULFILLED,
        payload: getResponsePipelineActions(resp.data),
      });
    } catch (error) {
      dispatch({
        type: ActionTypes.GET_ENTITY_PIPELINE_ACTIONS_FAILED,
        error: getApplicationError(error),
      });
      handleAsApplicationError(dispatch)(error);
    }
  };
}

// TODO: This doesn't appear to be used anywhere, we can probably remove it
export function clearEntityPipelineActions() {
  return {
    type: ActionTypes.CLEAR_ENTITY_PIPELINE_ACTIONS,
  };
}

// TODO: This doesn't appear to be used anywhere, we can probably remove it
export function clearFieldPipelineActions() {
  return {
    type: ActionTypes.CLEAR_FIELD_PIPELINE_ACTIONS,
  };
}
