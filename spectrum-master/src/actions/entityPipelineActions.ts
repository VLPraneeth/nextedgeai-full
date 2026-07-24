//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { each } from 'lodash';
import { Action } from 'redux';
import { ThunkAction } from 'redux-thunk';

import { ConfigContext } from 'components/skull';
import { SyncariThunkDispatch } from 'hooks/redux';
import { GroupConfiguration } from 'pages/sync-studio/NodePanel';
import { getEntities } from 'store/entity/thunks';
import { setTestPanelView } from 'store/test/actions';
import { getLiveTestRun, getLiveTestRuns } from 'store/test/thunks';
import { TestPanelView } from 'store/test/types';
import { RootState } from 'store/types';
import { get, post, request } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import { getErrorMessage, handleAsApplicationError, isResourceNotFound } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { t } from 'utils/i18nUtil';
import { thottlePromiseThunk } from 'utils/StoreUtil';
import { makeUrl, replaceToken } from 'utils/UrlUtil';

export const ActionTypes = {
  GET_ENTITY_PIPELINE: 'GET_ENTITY_PIPELINE',
  GET_ENTITY_PIPELINE_PENDING: 'GET_ENTITY_PIPELINE_PENDING',
  GET_ENTITY_PIPELINE_FULFILLED: 'GET_ENTITY_PIPELINE_FULFILLED',
  GET_ENTITY_PIPELINE_FAILED: 'GET_ENTITY_PIPELINE_FAILED',

  SAVE_ENTITY_PIPELINE_PENDING: 'SAVE_ENTITY_PIPELINE_PENDING',
  SAVE_ENTITY_PIPELINE_FULFILLED: 'SAVE_ENTITY_PIPELINE_FULFILLED',
  SAVE_ENTITY_PIPELINE_FAILED: 'SAVE_ENTITY_PIPELINE_FAILED',

  GET_ENTITY_SCHEMA_PENDING: 'GET_ENTITY_SCHEMA_PENDING',
  GET_ENTITY_SCHEMA_FULFILLED: 'GET_ENTITY_SCHEMA_FULFILLED',
  GET_ENTITY_SCHEMA_FAILED: 'GET_ENTITY_SCHEMA_FAILED',

  DISCARD_ENTITY_PIPELINE_PENDING: 'DISCARD_ENTITY_PIPELINE_PENDING',
  DISCARD_ENTITY_PIPELINE_FULFILLED: 'DISCARD_ENTITY_PIPELINE_FULFILLED',
  DISCARD_ENTITY_PIPELINE_FAILED: 'DISCARD_ENTITY_PIPELINE_FAILED',

  APPROVE_ENTITY_PIPELINE_PENDING: 'APPROVE_ENTITY_PIPELINE_PENDING',
  APPROVE_ENTITY_PIPELINE_FULFILLED: 'APPROVE_ENTITY_PIPELINE_FULFILLED',
  APPROVE_ENTITY_PIPELINE_FAILED: 'APPROVE_ENTITY_PIPELINE_FAILED',

  STOP_ENTITY_PIPELINE_PENDING: 'STOP_ENTITY_PIPELINE_PENDING',
  STOP_ENTITY_PIPELINE_FULFILLED: 'STOP_ENTITY_PIPELINE_FULFILLED',
  STOP_ENTITY_PIPELINE_FAILED: 'STOP_ENTITY_PIPELINE_FAILED',

  START_ENTITY_PIPELINE_PENDING: 'START_ENTITY_PIPELINE_PENDING',
  START_ENTITY_PIPELINE_FULFILLED: 'START_ENTITY_PIPELINE_FULFILLED',
  START_ENTITY_PIPELINE_FAILED: 'START_ENTITY_PIPELINE_FAILED',

  STOP_TRANSITIONING_PIPELINE_TOAST: 'STOP_TRANSITIONING_PIPELINE_TOAST',
  RESET_TRANSITIONING_PIPELINE: 'RESET_TRANSITIONING_PIPELINE',

  TEST_ENTITY_PIPELINE_PENDING: 'TEST_ENTITY_PIPELINE_PENDING',
  TEST_ENTITY_PIPELINE_FULFILLED: 'TEST_ENTITY_PIPELINE_FULFILLED',
  TEST_ENTITY_PIPELINE_FAILED: 'TEST_ENTITY_PIPELINE_FAILED',

  CLEAR_ENTITY_PIPELINE: 'CLEAR_ENTITY_PIPELINE',
  CLEAR_CONNECTOR_ENTITIES: 'CLEAR_CONNECTOR_ENTITIES',

  SHOW_NODE_CONFIG: 'SHOW_NODE_CONFIG',
  SET_SELECTED_GRAPH_NODE: 'SET_SELECTED_GRAPH_NODE',
  SET_NODE_CONFIG: 'SET_NODE_CONFIG',

  GET_CONNECTOR_ENTITIES_FOR_PIPELINE_PENDING: 'GET_CONNECTOR_ENTITIES_FOR_PIPELINE_PENDING',
  GET_CONNECTOR_ENTITIES_FOR_PIPELINE_FULFILLED: 'GET_CONNECTOR_ENTITIES_FOR_PIPELINE_FULFILLED',
  GET_CONNECTOR_ENTITIES_FOR_PIPELINE_FAILED: 'GET_CONNECTOR_ENTITIES_FOR_PIPELINE_FAILED',

  VALIDATE_ENTITY_PIPELINE_PENDING: 'VALIDATE_ENTITY_PIPELINE_PENDING',
  VALIDATE_ENTITY_PIPELINE_FULFILLED: 'VALIDATE_ENTITY_PIPELINE_FULFILLED',
  VALIDATE_ENTITY_PIPELINE_FAILED: 'VALIDATE_ENTITY_PIPELINE_FAILED',

  CLEAR_ERROR: 'CLEAR_ERROR',
  CLEAR_DYNAMIC_CONFIG: 'CLEAR_DYNAMIC_CONFIG',
  SET_GROUP_CONFIGURATION: 'SET_GROUP_CONFIGURATION',
  INITIALIZE_APPROVE_MODAL: 'INITIALIZE_APPROVE_MODAL',

  GET_FIELD_DRAFT_SUMMARY_FULFILLED: 'GET_FIELD_DRAFT_SUMMARY_FULFILLED',
  GET_FIELD_DRAFT_SUMMARY_PENDING: 'GET_FIELD_DRAFT_SUMMARY_PENDING',
  GET_FIELD_DRAFT_SUMMARY_FAILED: 'GET_FIELD_DRAFT_SUMMARY_FAILED',

  DELETE_ENTITY_PIPELINE_PENDING: 'DELETE_ENTITY_PIPELINE_PENDING',
  DELETE_ENTITY_PIPELINE_FULFILLED: 'DELETE_ENTITY_PIPELINE_FULFILLED',
  DELETE_ENTITY_PIPELINE_FAILED: 'DELETE_ENTITY_PIPELINE_FAILED',

  CREATE_DRAFT_ENTITY_PIPELINE_FULFILLED: 'CREATE_DRAFT_ENTITY_PIPELINE_FULFILLED',
  CREATE_DRAFT_ENTITY_PIPELINE_PENDING: 'CREATE_DRAFT_ENTITY_PIPELINE_PENDING',
  CREATE_DRAFT_ENTITY_PIPELINE_FAILED: 'CREATE_DRAFT_ENTITY_PIPELINE_FAILED',

  SHOW_PUBLISH_DRAFT_MODAL: 'SHOW_PUBLISH_DRAFT_MODAL',
  SHOW_DELETE_DRAFT_MODAL: 'SHOW_DELETE_DRAFT_MODAL',

  SET_PIPELINE_CONTEXT: 'SET_PIPELINE_CONTEXT',

  GET_ASYNC_NODE_CONFIG_PENDING: 'GET_ASYNC_NODE_CONFIG_PENDING',
  GET_ASYNC_NODE_CONFIG_FULFILLED: 'GET_ASYNC_NODE_CONFIG_FULFILLED',
  GET_ASYNC_NODE_CONFIG_FAILED: 'GET_ASYNC_NODE_CONFIG_FAILED',

  TEST_PIPELINE_DONE: 'TEST_PIPELINE_DONE',
  SYNC_SUCCESS: 'SYNC_SUCCESS',
  PIPELINE_EVENT: 'PIPELINE_EVENT',
  SET_GRAPH_FOR_PUBLISH_READY_ONLY: 'SET_GRAPH_FOR_PUBLISH_READY_ONLY',

  // Resync Entities
  RESYNC_ENTITY_SOURCES_PENDING: 'RESYNC_ENTITY_SOURCES_PENDING',
  RESYNC_ENTITY_SOURCES_FULFILLED: 'RESYNC_ENTITY_SOURCES_FULFILLED',
  RESYNC_ENTITY_SOURCES_FAILED: 'RESYNC_ENTITY_SOURCES_FAILED',

  SHOW_RESYNC_DRAFT_WARNING_MODAL: 'SHOW_RESYNC_DRAFT_WARNING_MODAL',
  SHOW_RESYNC_ENTITY_MODAL: 'SHOW_RESYNC_ENTITY_MODAL',

  CANCEL_RESYNC_PENDING: 'CANCEL_RESYNC_PENDING',
  CANCEL_RESYNC_FULFILLED: 'CANCEL_RESYNC_FULFILLED',
  CANCEL_RESYNC_FAILED: 'CANCEL_RESYNC_FAILED',

  GET_SYNC_STATUS_PENDING: 'GET_SYNC_STATUS_PENDING',
  GET_SYNC_STATUS_FULFILLED: 'GET_SYNC_STATUS_FULFILLED',
  GET_SYNC_STATUS_FAILED: 'GET_SYNC_STATUS_FAILED',

  GET_SYNC_STATUSES_PENDING: 'GET_SYNC_STATUSES_PENDING',
  GET_SYNC_STATUSES_FULFILLED: 'GET_SYNC_STATUSES_FULFILLED',
  GET_SYNC_STATUSES_FAILED: 'GET_SYNC_STATUSES_FAILED',

  GET_RESYNC_DETAILS_PENDING: 'GET_RESYNC_STATUSES_PENDING',
  GET_RESYNC_DETAILS_FULFILLED: 'GET_RESYNC_STATUSES_FULFILLED',
  GET_RESYNC_DETAILS_FAILED: 'GET_RESYNC_STATUSES_FAILED',

  SHOW_CLONE_PIPLINE_MODAL: 'SHOW_CLONE_PIPLINE_MODAL',
};

/**
 * Get the entity pipeline
 */
export function getEntityPipeline(entityId: string, pipelineVersion?: 'NEW' | 'APPROVED') {
  // Based on the graphStatus (NEW/APPROVED) we request just
  // the nodes/edges we need from the backend instead of getting
  // nodes/edges for both published and draft pipelines. See SYN-12239
  let url = DataUrlConstants.ENTITY_PIPELINE;
  if (pipelineVersion === AppConstants.GRAPH_STATUS.NEW) {
    url = DataUrlConstants.ENTITY_PIPELINE_DRAFT;
  } else if (pipelineVersion === AppConstants.GRAPH_STATUS.APPROVED) {
    url = DataUrlConstants.ENTITY_PIPELINE_APPROVED;
  }
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_ENTITY_PIPELINE_PENDING,
    });
    const params = { entityId };
    get(replaceToken(url, params))
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        if (isResourceNotFound(error)) {
          dispatch({
            type: ActionTypes.GET_ENTITY_PIPELINE_FAILED,
            error: getErrorMessage(error),
            exists: false,
          });
        } else {
          dispatch({
            type: ActionTypes.GET_ENTITY_PIPELINE_FAILED,
            error: getErrorMessage(error),
          });

          handleAsApplicationError(dispatch);
        }
      });
  };
}

export function createDraftEntityPipeline(entityId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.CREATE_DRAFT_ENTITY_PIPELINE_PENDING,
    });
    const url = replaceToken(DataUrlConstants.CREATE_ENTITY_PIPELINE, { entityId });
    return post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.CREATE_DRAFT_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.CREATE_DRAFT_ENTITY_PIPELINE_FAILED,
          error: error.response.data,
        });
      });
  };
}

export function updateEntityPipeline(
  entityId: string | null | undefined,
  graphJson: any,
  options: { refreshPipelineOnUpdate?: boolean } = {}
) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.SAVE_ENTITY_PIPELINE_PENDING,
    });
    dispatch(clearError());

    return request({
      url: replaceToken(DataUrlConstants.ENTITY_PIPELINE, { entityId }),
      method: 'POST',
      data: JSON.stringify(graphJson),
    })
      .then((resp) => {
        dispatch({
          type: ActionTypes.SAVE_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
        });

        if (options?.refreshPipelineOnUpdate && entityId) {
          dispatch(getEntityPipeline(entityId));
          dispatch(getEntities());
        }
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.SAVE_ENTITY_PIPELINE_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function showEntityPipelineError(message: string) {
  return {
    type: ActionTypes.SAVE_ENTITY_PIPELINE_FAILED,
    error: {
      message,
    },
  };
}

export function approveEntityPipeline(
  entityId: string,
  versionInfo: {
    name: string;
    summary: string;
  },
  processAll = false,
  readyOnly = true,
  graph = null
) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.APPROVE_ENTITY_PIPELINE_PENDING,
    });
    const params = { entityId };
    const url = replaceToken(DataUrlConstants.APPROVE_ENTITY_PIPELINE, params);
    post(url, { processAll, readyOnly, graph, versionInfo })
      .then((resp) => {
        dispatch({
          type: ActionTypes.APPROVE_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.APPROVE_ENTITY_PIPELINE_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function setGraphForPublishReadyOnly(graph: any) {
  return {
    type: ActionTypes.SET_GRAPH_FOR_PUBLISH_READY_ONLY,
    graph,
  };
}

export function initializeApproveModal() {
  return {
    type: ActionTypes.INITIALIZE_APPROVE_MODAL,
  };
}

export function discardEntityPipeline(
  entityId: string,
  options: { versionInfo?: { name: string; summary: string }; refreshPipelineOnDelete?: boolean } = {}
) {
  const { versionInfo } = options;

  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.DISCARD_ENTITY_PIPELINE_PENDING,
    });
    const params = { entityId };
    const url = replaceToken(DataUrlConstants.DISCARD_ENTITY_PIPELINE, params);
    post(url, { versionInfo })
      .then((resp) => {
        dispatch({
          type: ActionTypes.DISCARD_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
        });

        const { refreshPipelineOnDelete } = options;
        if (refreshPipelineOnDelete) {
          getEntityPipeline(entityId)(dispatch);
        }
        dispatch(getEntities());
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.DISCARD_ENTITY_PIPELINE_FAILED,
          error: error.response,
        });
      });
  };
}

export function getConnectorsSchema(connectors: any[]) {
  return (dispatch: SyncariThunkDispatch) => {
    each(connectors, (connector) => {
      if (connector.status === AppConstants.CONNECTOR_STATUS.ACTIVE) {
        const params = {
          connectorId: connector.id,
        };
        get(replaceToken(DataUrlConstants.CONNECTOR_ENTITIES, params))
          .then((resp) => {
            dispatch({
              type: ActionTypes.GET_ENTITY_SCHEMA_FULFILLED,
              payload: resp.data,
              connectorId: connector.id,
            });
          })
          .catch(handleAsApplicationError(dispatch));
      }
    });
  };
}
/**
 * Clear the application states of the entity pipeline
 */
export function clearEntityPipeline() {
  return {
    type: ActionTypes.CLEAR_ENTITY_PIPELINE,
  };
}

export function clearConnectorEntities() {
  return {
    type: ActionTypes.CLEAR_CONNECTOR_ENTITIES,
  };
}

export function getConnectorEntities(entityId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_CONNECTOR_ENTITIES_FOR_PIPELINE_PENDING,
    });

    get(replaceToken(DataUrlConstants.CONNECTOR_ENTITIES, { entityId }))
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_CONNECTOR_ENTITIES_FOR_PIPELINE_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_CONNECTOR_ENTITIES_FOR_PIPELINE_FAILED,
        });
        handleAsApplicationError(dispatch)(error);
      });
  };
}
// TODO: Separate the name metadata
export function showNodeConfigModal(visible: boolean, context?: ConfigContext, name?: string | null) {
  return {
    type: ActionTypes.SHOW_NODE_CONFIG,
    context,
    visible,
    name,
  };
}

export function setSelectedGraphNode(nodeModel?: any) {
  return {
    type: ActionTypes.SET_SELECTED_GRAPH_NODE,
    selectedGraphNode: nodeModel,
  };
}

export function setNodeConfig(nodeConfig: any) {
  return {
    type: ActionTypes.SET_NODE_CONFIG,
    nodeConfig,
  };
}

export function validate(entityId: string, draftGraph: any) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.VALIDATE_ENTITY_PIPELINE_PENDING,
    });

    const url = replaceToken(DataUrlConstants.VALIDATE_ENTITY_PIPELINE, {
      entityId,
    });

    dispatch(clearError());

    return post(url, draftGraph)
      .then((resp) => {
        dispatch({
          type: ActionTypes.VALIDATE_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((err) => {
        let error = err.response.data;

        if (err.response.status === 502) {
          // The response is a 500 when there are validation errors. If we get a 502
          // the validation call failed entirely (or timed out) so we should show a
          // custom error message.
          error = {
            status: '502',
            validationErrors: [
              {
                level: 'GLOBAL',
                type: 'ERROR',
                message: t('ApiUtil.server_error_message'),
              },
            ],
          };
        } else if (!err.response.data.validationErrors) {
          // If we recieve a validation error that is flat, i.e. no validationErrors
          // array, then transform the response into a form we are expecting and
          // display it as a global error. NOTE: since the error returned from the
          // backend would be flat in this case, this error will block other validation
          // errors until it is resolved.
          error = {
            status: '500',
            validationErrors: [
              {
                level: 'GLOBAL',
                type: 'ERROR',
                message: err.response.data.message,
              },
            ],
          };
        }

        dispatch({
          type: ActionTypes.VALIDATE_ENTITY_PIPELINE_FAILED,
          error,
        });
      });
  };
}

export function clearError() {
  return {
    type: ActionTypes.CLEAR_ERROR,
  };
}

export function getFieldDraftSummary(entityId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    const url = replaceToken(DataUrlConstants.FIELD_DRAFT_SUMMARY, { entityId });
    dispatch({
      type: ActionTypes.GET_FIELD_DRAFT_SUMMARY_PENDING,
    });
    get(url)
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_FIELD_DRAFT_SUMMARY_FULFILLED,
          payload: resp.data,
          entityId,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_FIELD_DRAFT_SUMMARY_FAILED,
        });
        handleAsApplicationError(dispatch)(error);
      });
  };
}

export function showPublishDraftModal(visible = true, entityId?: string, hasUnpublishedSynapse?: boolean) {
  return {
    type: ActionTypes.SHOW_PUBLISH_DRAFT_MODAL,
    visible,
    publishDraftModalEntityId: entityId,
    hasUnpublishedSynapse,
  };
}

export function showDeleteDraftModal(visible = true, entityId?: string, refreshOnDelete?: boolean) {
  return {
    type: ActionTypes.SHOW_DELETE_DRAFT_MODAL,
    visible,
    deleteDraftModalEntityId: entityId,
    deleteDraftModalRefreshOnDelete: refreshOnDelete,
  };
}

export function showClonePipelineModal(
  visible: boolean,
  entityId: string,
  isDraft: boolean,
  pipelineName: string = ''
) {
  return {
    type: ActionTypes.SHOW_CLONE_PIPLINE_MODAL,
    visible,
    entityId,
    isDraft,
    pipelineName,
  };
}

export function deletePublishedPipeline(entityId: string, options: { refreshPipelineOnDelete?: string } = {}) {
  return (dispatch: SyncariThunkDispatch) => {
    const url = replaceToken(DataUrlConstants.DELETE_ENTITY_PIPELINE, { entityId });
    dispatch({
      type: ActionTypes.DELETE_ENTITY_PIPELINE_PENDING,
    });
    post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.DELETE_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
          entityId,
        });

        const { refreshPipelineOnDelete } = options;
        if (refreshPipelineOnDelete) {
          getEntityPipeline(entityId)(dispatch);
        }
        dispatch(getEntities());
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.DELETE_ENTITY_PIPELINE_FAILED,
        });
        handleAsApplicationError(dispatch)(error);
      });
  };
}

export function setPipelineContext(pipelineContext: any) {
  return {
    type: ActionTypes.SET_PIPELINE_CONTEXT,
    pipelineContext,
  };
}

export const getAsyncNodeConfig = (
  nodeId: string,
  graphJson?: Record<string, any> | null,
  params?: Record<string, any>
): ThunkAction<void, RootState, unknown, Action<string>> => (dispatch) =>
  thottlePromiseThunk('getAsyncNodeConfig', () => {
    dispatch({
      type: ActionTypes.GET_ASYNC_NODE_CONFIG_PENDING,
      nodeId,
    });

    return post(makeUrl(DataUrlConstants.ASYNC_NODE_CONFIG, { nodeId }, params), graphJson)
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_ASYNC_NODE_CONFIG_FULFILLED,
          nodeId,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_ASYNC_NODE_CONFIG_FAILED,
          nodeId,
          error: getErrorMessage(error),
        });
      });
  });

export function clearDynamicNodeConfig() {
  return {
    type: ActionTypes.CLEAR_DYNAMIC_CONFIG,
  };
}

export function setGroupConfiguration(groupConfiguration?: GroupConfiguration) {
  return {
    type: ActionTypes.SET_GROUP_CONFIGURATION,
    groupConfiguration,
  };
}

export function stop(entityId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.STOP_ENTITY_PIPELINE_PENDING,
      entityId,
    });
    const params = { entityId };
    const url = replaceToken(DataUrlConstants.STOP_ENTITY_PIPELINE, params);
    post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.STOP_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
          entityId,
        });
        dispatch(getSyncStatuses());
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.STOP_ENTITY_PIPELINE_FAILED,
          error: error.response,
          entityId,
        });
      });
  };
}

export function start(entityId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.START_ENTITY_PIPELINE_PENDING,
      entityId,
    });
    const params = { entityId };
    const url = replaceToken(DataUrlConstants.START_ENTITY_PIPELINE, params);
    post(url, {})
      .then((resp) => {
        dispatch({
          type: ActionTypes.START_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
          entityId,
        });
        dispatch(getSyncStatuses());
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.START_ENTITY_PIPELINE_FAILED,
          error: error.response,
          entityId,
        });
      });
  };
}

export function stopTransitioningPipelineToast() {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.STOP_TRANSITIONING_PIPELINE_TOAST,
    });
  };
}

export function resetTransitioningPipeline() {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.RESET_TRANSITIONING_PIPELINE,
    });
  };
}

export function testPipeline(params: any, entityId: string | null | undefined, graphId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.TEST_ENTITY_PIPELINE_PENDING,
    });
    const url = replaceToken(DataUrlConstants.TEST_ENTITY_PIPELINE, { entityId });
    post(url, params)
      .then((resp) => {
        dispatch({
          type: ActionTypes.TEST_ENTITY_PIPELINE_FULFILLED,
          payload: resp.data,
        });
        if (entityId) {
          dispatch(getEntityPipeline(entityId));
        }
        // Navigate to the test results panel after a live test has been started.
        dispatch(setTestPanelView(TestPanelView.LIVE_RESULTS));

        // After starting a live test we fetch the test runs and the latest test
        // run to show the processing test run to the user
        dispatch(getLiveTestRuns({ graphId }));
        dispatch(getLiveTestRun({ graphId, runId: 'latest' }));
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.TEST_ENTITY_PIPELINE_FAILED,
          error: getErrorMessage(error),
        });
        // We need to run getEntityPipeline even on failure to reset the
        // readOnly value of the current draft which is set to true when the
        // original request is fired.
        if (entityId) {
          getEntityPipeline(entityId)(dispatch);
        }
      });
  };
}

export function resyncEntityPipelineForSources(payload: any) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.RESYNC_ENTITY_SOURCES_PENDING,
      payload,
    });

    const { entityId, sourceEntityIds, fromDate, toDate } = payload;

    return post(replaceToken(DataUrlConstants.RESYNC_ENTITY_SOURCE, { entityId }), {
      fromDate,
      toDate,
      synapseEntityIds: sourceEntityIds,
    })
      .then((resp) => {
        dispatch({
          type: ActionTypes.RESYNC_ENTITY_SOURCES_FULFILLED,
          payload,
        });

        dispatch(showResyncModal(false));
        dispatch(getResyncDetails(entityId));
        dispatch(getSyncStatuses());
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.RESYNC_ENTITY_SOURCES_FAILED,
          payload,
          error: getErrorMessage(error),
        });
      });
  };
}

export const showResyncDraftWarningModal = (flag = true) => ({
  type: ActionTypes.SHOW_RESYNC_DRAFT_WARNING_MODAL,
  payload: {
    isVisible: flag,
  },
});

export const showResyncModal = (flag = true) => ({
  type: ActionTypes.SHOW_RESYNC_ENTITY_MODAL,
  payload: {
    isVisible: flag,
  },
});

export function getSyncStatus(entityId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_SYNC_STATUS_PENDING,
      entityId,
    });

    return get(replaceToken(DataUrlConstants.ENTITY_PIPELINE_STATUS, { entityId }))
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_SYNC_STATUS_FULFILLED,
          payload: resp.data,
          entityId,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_SYNC_STATUS_FAILED,
          error: getErrorMessage(error),
          entityId,
        });
      });
  };
}

export function getSyncStatuses() {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_SYNC_STATUSES_PENDING,
    });

    return get(DataUrlConstants.ENTITY_PIPELINE_STATUSES)
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_SYNC_STATUSES_FULFILLED,
          payload: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_SYNC_STATUSES_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function cancelResync(entityId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.CANCEL_RESYNC_PENDING,
    });
    return post(replaceToken(DataUrlConstants.CANCEL_RESYNC_ENTITY, { entityId }))
      .then((resp) => {
        dispatch({
          type: ActionTypes.CANCEL_RESYNC_FULFILLED,
          payload: resp.data,
        });
        dispatch(getResyncDetails(entityId));
        dispatch(getSyncStatuses());
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.CANCEL_RESYNC_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function getResyncDetails(entityId: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_RESYNC_DETAILS_PENDING,
      payload: {
        entityId,
      },
    });
    return get(replaceToken(DataUrlConstants.GET_RESYNC_DETAILS, { entityId }))
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_RESYNC_DETAILS_FULFILLED,
          payload: {
            entityId,
            resyncDetails: resp.data,
          },
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_RESYNC_DETAILS_FAILED,
          payload: {
            entityId,
            error: getErrorMessage(error),
          },
        });
      });
  };
}
