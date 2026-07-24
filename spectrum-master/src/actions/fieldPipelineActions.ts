//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { SyncariThunkDispatch } from 'hooks/redux';
import { graphChanged } from 'store/pipeline/actions';
import { getSchemaForEntity } from 'store/schema/thunks';
import { get, post, request } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import { getErrorMessage, handleAsApplicationError, isResourceNotFound } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { replaceToken } from 'utils/UrlUtil';

export const ActionTypes = {
  GET_FIELD_PIPELINE: 'GET_FIELD_PIPELINE',
  GET_FIELD_PIPELINE_PENDING: 'GET_FIELD_PIPELINE_PENDING',
  GET_FIELD_PIPELINE_FULFILLED: 'GET_FIELD_PIPELINE_FULFILLED',
  GET_FIELD_PIPELINE_FAILED: 'GET_FIELD_PIPELINE_FAILED',

  SAVE_FIELD_PIPELINE_PENDING: 'SAVE_FIELD_PIPELINE_PENDING',
  SAVE_FIELD_PIPELINE_FULFILLED: 'SAVE_FIELD_PIPELINE_FULFILLED',
  SAVE_FIELD_PIPELINE_FAILED: 'SAVE_FIELD_PIPELINE_FAILED',

  APPROVE_FIELD_PIPELINE_PENDING: 'APPROVE_FIELD_PIPELINE_PENDING',
  APPROVE_FIELD_PIPELINE_FULFILLED: 'APPROVE_FIELD_PIPELINE_FULFILLED',
  APPROVE_FIELD_PIPELINE_FAILED: 'APPROVE_FIELD_PIPELINE_FAILED',

  GET_ATTRIBUTE_NODES_PENDING: 'GET_ATTRIBUTE_NODES_PENDING',
  GET_ATTRIBUTE_NODES_FAILED: 'GET_ATTRIBUTE_NODES_FAILED',
  GET_ATTRIBUTE_NODES_FULFILLED: 'GET_ATTRIBUTE_NODES_FULFILLED',

  CREATE_DRAFT_FIELD_PIPELINE_FULFILLED: 'CREATE_DRAFT_FIELD_PIPELINE_FULFILLED',
  CREATE_DRAFT_FIELD_PIPELINE_PENDING: 'CREATE_DRAFT_FIELD_PIPELINE_PENDING',
  CREATE_DRAFT_FIELD_PIPELINE_FAILED: 'CREATE_DRAFT_FIELD_PIPELINE_FAILED',

  MARK_FIELD_PIPELINE_READY_PENDING: 'MARK_FIELD_PIPELINE_READY_PENDING',
  MARK_FIELD_PIPELINE_READY_FULFILLED: 'MARK_FIELD_PIPELINE_READY_FULFILLED',
  MARK_FIELD_PIPELINE_READY_FAILED: 'MARK_FIELD_PIPELINE_READY_FAILED',

  MARK_FIELD_PIPELINE_NOT_READY_PENDING: 'MARK_FIELD_PIPELINE_NOT_READY_PENDING',
  MARK_FIELD_PIPELINE_NOT_READY_FULFILLED: 'MARK_FIELD_PIPELINE_NOT_READY_FULFILLED',
  MARK_FIELD_PIPELINE_NOT_READY_FAILED: 'MARK_FIELD_PIPELINE_NOT_READY_FAILED',

  DISCARD_FIELD_PIPELINE_FULFILLED: 'DISCARD_FIELD_PIPELINE_FULFILLED',
  DISCARD_FIELD_PIPELINE_PENDING: 'DISCARD_FIELD_PIPELINE_PENDING',
  DISCARD_FIELD_PIPELINE_FAILED: 'DISCARD_FIELD_PIPELINE_FAILED',

  DELETE_FIELD_PIPELINE_FULFILLED: 'DELETE_FIELD_PIPELINE_FULFILLED',
  DELETE_FIELD_PIPELINE_PENDING: 'DELETE_FIELD_PIPELINE_PENDING',
  DELETE_FIELD_PIPELINE_FAILED: 'DELETE_FIELD_PIPELINE_FAILED',

  VALIDATE_FIELD_PIPELINE_PENDING: 'VALIDATE_FIELD_PIPELINE_PENDING',
  VALIDATE_FIELD_PIPELINE_FULFILLED: 'VALIDATE_FIELD_PIPELINE_FULFILLED',
  VALIDATE_FIELD_PIPELINE_FAILED: 'VALIDATE_FIELD_PIPELINE_FAILED',

  CLEAR_ERROR: 'CLEAR_ERROR',
  CLEAR_FIELD_PIPELINE: 'CLEAR_FIELD_PIPELINE',
  CLEAR_ATTRIBUTE_NODES: 'CLEAR_ATTRIBUTE_NODES',
};

/**
 * Get the field pipeline
 */
export function getFieldPipeline(entityId: string, fieldId: string, pipelineVersion?: 'NEW' | 'APPROVED') {
  // Based on the graphStatus (NEW/APPROVED) we request just
  // the nodes/edges we need from the backend instead of getting
  // nodes/edges for both published and draft pipelines. See SYN-12239
  let url = DataUrlConstants.FIELD_PIPELINE;
  if (pipelineVersion === AppConstants.GRAPH_STATUS.NEW) {
    url = DataUrlConstants.FIELD_PIPELINE_DRAFT;
  } else if (pipelineVersion === AppConstants.GRAPH_STATUS.APPROVED) {
    url = DataUrlConstants.FIELD_PIPELINE_APPROVED;
  }

  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_FIELD_PIPELINE_PENDING,
    });
    const params = { entityId, fieldId };
    get(replaceToken(url, params))
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_FIELD_PIPELINE_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        if (isResourceNotFound(error)) {
          dispatch({
            type: ActionTypes.GET_FIELD_PIPELINE_FAILED,
            exists: false,
          });
        } else {
          dispatch({
            type: ActionTypes.GET_FIELD_PIPELINE_FAILED,
            error: getErrorMessage(error),
          });
          handleAsApplicationError(dispatch)(error);
        }
      });
  };
}

export function updateFieldPipeline(
  fieldId: string,
  graphJson: any,
  options: { entityId?: string; refreshPipelineOnUpdate?: boolean } = {}
) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(clearError());
    dispatch({
      type: ActionTypes.SAVE_FIELD_PIPELINE_PENDING,
    });
    const params = { fieldId };
    const url = replaceToken(DataUrlConstants.FIELD_PIPELINE, params);
    return request({
      url,
      method: 'POST',
      data: JSON.stringify(graphJson),
    })
      .then((resp) => {
        dispatch({
          type: ActionTypes.SAVE_FIELD_PIPELINE_FULFILLED,
          payload: resp.data,
        });
        const { refreshPipelineOnUpdate } = options;
        if (refreshPipelineOnUpdate && options.entityId) {
          getFieldPipeline(options.entityId, fieldId)(dispatch);
        }
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.SAVE_FIELD_PIPELINE_FAILED,
          error: error.response.data,
        });
      });
  };
}

export function approveFieldPipeline(fieldId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.APPROVE_FIELD_PIPELINE_PENDING,
    });
    const params = { fieldId };
    const url = replaceToken(DataUrlConstants.APPROVE_FIELD_PIPELINE, params);
    post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.APPROVE_FIELD_PIPELINE_FULFILLED,
          payload: resp.data,
        });
      })
      .catch(handleAsApplicationError(dispatch));
  };
}

export function createDraftFieldPipeline(fieldId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.CREATE_DRAFT_FIELD_PIPELINE_PENDING,
    });
    const url = replaceToken(DataUrlConstants.CREATE_FIELD_PIPELINE, { fieldId });
    return post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.CREATE_DRAFT_FIELD_PIPELINE_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.CREATE_DRAFT_FIELD_PIPELINE_FAILED,
          error: error.response.data,
        });
      });
  };
}

export function markFieldPipelineReady(entityId: string, fieldId: string, isMapped = true) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.MARK_FIELD_PIPELINE_READY_PENDING,
      entityId,
      fieldId,
    });
    const url = replaceToken(DataUrlConstants.MARK_FIELD_PIPELINE_READY, { fieldId });
    post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.MARK_FIELD_PIPELINE_READY_FULFILLED,
          entityId,
          fieldId,
          isMapped,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.MARK_FIELD_PIPELINE_READY_FAILED,
          entityId,
          fieldId,
          error: error.response.data,
        });
      });
  };
}

export function markFieldPipelineNotReady(entityId: string, fieldId: string, isMapped = true) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.MARK_FIELD_PIPELINE_NOT_READY_PENDING,
      entityId,
      fieldId,
    });
    const url = replaceToken(DataUrlConstants.MARK_FIELD_PIPELINE_NOT_READY, { fieldId });
    post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.MARK_FIELD_PIPELINE_NOT_READY_FULFILLED,
          entityId,
          fieldId,
          isMapped,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.MARK_FIELD_PIPELINE_NOT_READY_FAILED,
          entityId,
          fieldId,
          error: error.response.data,
        });
      });
  };
}

export function getAttributeNodes(attributeId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_ATTRIBUTE_NODES_PENDING,
    });

    const url = replaceToken(DataUrlConstants.ATTRIBUTE_NODES, { attributeId });
    get(url)
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_ATTRIBUTE_NODES_FULFILLED,
          payload: resp.data,
        });
      })
      .catch(handleAsApplicationError(dispatch));
  };
}

export function validate(fieldId: string, draftGraph: any) {
  return (dispatch: SyncariThunkDispatch) => {
    const url = replaceToken(DataUrlConstants.VALIDATE_FIELD_PIPELINE, {
      fieldId,
    });

    dispatch({
      type: ActionTypes.VALIDATE_FIELD_PIPELINE_PENDING,
    });

    dispatch(clearError());

    return post(url, draftGraph)
      .then((resp) => {
        dispatch({
          type: ActionTypes.VALIDATE_FIELD_PIPELINE_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.VALIDATE_FIELD_PIPELINE_FAILED,
          error: error.response.data,
        });
      });
  };
}

export function clearError() {
  return {
    type: ActionTypes.CLEAR_ERROR,
  };
}

export function clearFieldPipeline() {
  return {
    type: ActionTypes.CLEAR_FIELD_PIPELINE,
  };
}

export function clearAttributeNodes() {
  return {
    type: ActionTypes.CLEAR_ATTRIBUTE_NODES,
  };
}

export function discardFieldPipeline(
  fieldId: string,
  options: { refreshOnDiscard?: any; entityId?: any; refreshSchemaForEntity?: any; graphVersion?: any } = {}
) {
  return (dispatch: SyncariThunkDispatch) => {
    // If the pipeline had been changed, don't prompt the user to save
    // changes since the pipeline has been deleted.
    dispatch(
      graphChanged({
        changed: null,
        changedScope: null,
        changedId: null,
      })
    );

    dispatch({
      type: ActionTypes.DISCARD_FIELD_PIPELINE_PENDING,
    });
    const params = { fieldId };
    const url = replaceToken(DataUrlConstants.DISCARD_FIELD_PIPELINE, params);
    post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.DISCARD_FIELD_PIPELINE_FULFILLED,
          payload: resp.data,
        });

        const { refreshOnDiscard, entityId, refreshSchemaForEntity, graphVersion } = options;

        if (refreshOnDiscard) {
          getFieldPipeline(entityId, fieldId)(dispatch);
        }
        if (refreshSchemaForEntity) {
          dispatch(getSchemaForEntity({ entityId, graphVersion }));
        }
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.DISCARD_FIELD_PIPELINE_FAILED,
          error: error.response.data,
        });
      });
  };
}

export function deleteFieldPipeline(fieldId: string, options: { refreshPipeline?: boolean; entityId?: string } = {}) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.DELETE_FIELD_PIPELINE_PENDING,
    });
    const params = { fieldId };
    const url = replaceToken(DataUrlConstants.DELETE_FIELD_PIPELINE, params);
    post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.DELETE_FIELD_PIPELINE_FULFILLED,
          payload: resp.data,
        });

        const { refreshPipeline, entityId } = options;
        if (refreshPipeline && entityId) {
          getFieldPipeline(entityId, fieldId)(dispatch);
        }
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.DELETE_FIELD_PIPELINE_FAILED,
          error: error.response.data,
        });
      });
  };
}

export function showFieldPipelineError(message: string) {
  return {
    type: ActionTypes.SAVE_FIELD_PIPELINE_FAILED,
    error: {
      message,
    },
  };
}
