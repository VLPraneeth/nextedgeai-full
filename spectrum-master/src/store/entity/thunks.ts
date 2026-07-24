//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Action } from 'redux';
import { ThunkAction as BaseThunkAction } from 'redux-thunk';

import { SyncariThunkDispatch } from 'hooks/redux';
import { PromiseThunkAction } from 'store/types';
import { deleteRequest, get, post } from 'utils/AjaxUtil';
import { ResponseError, getErrorMessage, handleAsApplicationError } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { getResponseEntities, getResponseFields } from 'utils/EntityUtil';
import { thottlePromiseThunk } from 'utils/StoreUtil';
import { makeUrl, replaceToken } from 'utils/UrlUtil';

import { RootState } from '../../reducers';
import {
  deleteEntityFailed,
  deleteEntityFulfilled,
  deleteEntityPending,
  getConnectorEntitiesFailed,
  getConnectorEntitiesFulfilled,
  getConnectorEntitiesPending,
  getEntitiesFailed,
  getEntitiesFulfilled,
  getEntitiesPending,
  getEntityFieldsFailed,
  getEntityFieldsFulfilled,
  getEntityFieldsPending,
  getEntityMappingFulfilled,
  getEntityMappingPending,
  getEntityRecordsCountsFailed,
  getEntityRecordsCountsFulfilled,
  getEntityRecordsCountsPending,
  getFieldMappingFulfilled,
  getFieldMappingPending,
  saveEntityMappingFulfilled,
  saveEntityMappingPending,
  saveFieldMappingFailed,
  saveFieldMappingFulfilled,
  saveFieldMappingPending,
  showConnectorFieldModal,
} from './actions';
import { Entity, SHOW_QUICK_START_PUBLISH } from './types';

type ThunkAction = BaseThunkAction<void, RootState, unknown, Action<string>>;

/**
 * Get the list of entities
 */
export var getEntities = (): ThunkAction => (dispatch) =>
  thottlePromiseThunk('getEntities', () => {
    dispatch(getEntitiesPending());
    return get(DataUrlConstants.ENTITIES)
      .then((resp) => {
        dispatch(getEntitiesFulfilled(getResponseEntities(resp?.data, '')));
      })
      .catch((error) => {
        dispatch(getEntitiesFailed(error?.response?.data));
        handleAsApplicationError(dispatch)(error);
      });
  });

export var deleteEntity = (
  entityId: string,
  refresh = false
): PromiseThunkAction<{ success: boolean; error?: ResponseError }> => {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(deleteEntityPending());

    return deleteRequest(
      replaceToken(DataUrlConstants.ENTITY, {
        syncariEntityId: entityId,
      })
    )
      .then((resp) => {
        dispatch(deleteEntityFulfilled());

        if (refresh) {
          dispatch(getEntities());
        }

        return { success: true };
      })
      .catch((error) => {
        const errMsg = getErrorMessage(error);

        dispatch(deleteEntityFailed(errMsg));
        return { success: false, error: errMsg };
      });
  };
};

export var getConnectorEntities = (
  connectorId: string,
  detailed: boolean = true,
  showPending: boolean = true
): ThunkAction => (dispatch) => {
  thottlePromiseThunk(`getConnectorEntities${connectorId}${detailed}`, () => {
    if (showPending) {
      dispatch(getConnectorEntitiesPending(connectorId, detailed));
    }

    return get<Entity>(replaceToken(DataUrlConstants.SYNAPSE_ENTITIES, { connectorId, detailed }))
      .then((resp) => {
        dispatch(getConnectorEntitiesFulfilled(getResponseEntities(resp.data, connectorId, detailed)));
      })
      .catch((error) => {
        dispatch(getConnectorEntitiesFailed(error.response.data, connectorId, detailed));
        dispatch(handleAsApplicationError(error));
      });
  });
};

export var getEntityFields = (syncariEntityId: string): ThunkAction => (dispatch) => {
  thottlePromiseThunk(`getEntityFields${syncariEntityId}`, () => {
    dispatch(getEntityFieldsPending(syncariEntityId));
    return get(replaceToken(DataUrlConstants.ENTITY, { syncariEntityId }))
      .then((resp) => {
        dispatch(getEntityFieldsFulfilled(syncariEntityId, getResponseFields(syncariEntityId, resp.data)));
      })
      .catch((error) => {
        dispatch(getEntityFieldsFailed(syncariEntityId, error));
      });
  });
};

export function getEntityMapping(connectorId: string): ThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(getEntityMappingPending());

    return get(replaceToken(DataUrlConstants.ENTITY_MAPPING, { connectorId }))
      .then((resp) => {
        dispatch(getEntityMappingFulfilled(resp.data));
      })
      .catch(handleAsApplicationError(dispatch));
  };
}

interface SaveEntityMappingOptions {
  refreshEntities?: boolean;
}

export function saveEntityMapping(
  connectorId: string,
  entityMapping: any,
  options: SaveEntityMappingOptions = {}
): ThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(saveEntityMappingPending());

    return post(replaceToken(DataUrlConstants.ENTITY_MAPPING, { connectorId }), entityMapping)
      .catch(handleAsApplicationError(dispatch))
      .finally(() => {
        dispatch(saveEntityMappingFulfilled());
        if (options.refreshEntities) {
          dispatch(getEntities());
        }
      });
  };
}

export function getFieldMapping(syncariEntityId: string, synapseEntityId: string): ThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(getFieldMappingPending());
    get(
      replaceToken(DataUrlConstants.FIELD_MAPPING, {
        syncariEntityId,
        synapseEntityId,
      })
    )
      .then((resp) => {
        dispatch(getFieldMappingFulfilled(resp.data));
      })
      .catch(handleAsApplicationError(dispatch));
  };
}

interface SaveFieldMappingOptions {
  refreshEntities?: boolean;
  refreshFields?: boolean;
}

// TODO: improve return typing and API responses
export function saveFieldMapping(
  connectorId: string,
  fieldMapping: any,
  options: SaveFieldMappingOptions = {}
): BaseThunkAction<Promise<any>, RootState, unknown, Action<string>> {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(saveFieldMappingPending());

    return post(replaceToken(DataUrlConstants.CREATE_FIELD_MAPPING, { connectorId }), fieldMapping)
      .then((resp) => {
        // send field mapping data to FULFILLMENT to save into state with reducer
        dispatch(saveFieldMappingFulfilled(resp.data));

        if (options.refreshEntities || options.refreshFields) {
          dispatch(getEntities());
        }

        dispatch(showConnectorFieldModal(false));
      })
      .catch((error) => dispatch(saveFieldMappingFailed(error.response?.data)));
  };
}

export var getEntityRecordsCounts = (entityApiNames: string[]): ThunkAction => {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch(getEntityRecordsCountsPending(entityApiNames));

    return get(makeUrl(DataUrlConstants.GET_ENTITY_RECORDS_COUNT, null, { entityApiNames }))
      .then((resp) => {
        dispatch(getEntityRecordsCountsFulfilled(resp.data));
      })
      .catch((err) => {
        dispatch(getEntityRecordsCountsFailed(entityApiNames));
      });
  };
};

export var showQuickStartPublish = (visible: boolean, quickStartId: string) => {
  return {
    type: SHOW_QUICK_START_PUBLISH,
    payload: {
      visible,
      quickStartId,
    },
  };
};
