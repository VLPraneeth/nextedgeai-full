import { Action } from 'redux';
import { ThunkAction as BaseThunkAction } from 'redux-thunk';

import { getEntities } from 'store/entity/thunks';
import { PromiseThunkAction } from 'store/types';
import { deleteRequest, get, post, put } from 'utils/AjaxUtil';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { replaceToken } from 'utils/StringUtil';

import { RootState } from '../../reducers';
import {
  createEntityDraftFailed,
  createEntityDraftFulfilled,
  createEntityDraftPending,
  discardEntitySchemaFailed,
  discardEntitySchemaFulfilled,
  discardEntitySchemaPending,
  getConnectorEntitySchemaFailed,
  getConnectorEntitySchemaFulfilled,
  getConnectorEntitySchemaPending,
  getConnectorSchemaFailed,
  getConnectorSchemaFulfilled,
  getConnectorSchemaPending,
  getEntityDetailFailed,
  getEntityDetailFulfilled,
  getEntityDetailPending,
  getEntitySchemaForVersionFailed,
  getEntitySchemaForVersionFulfilled,
  getEntitySchemaForVersionPending,
  publishEntitySchemaFailed,
  publishEntitySchemaFulfilled,
  publishEntitySchemaPending,
  saveEntityFailed,
  saveEntityFulfilled,
  saveEntityPending,
  saveFieldFailed,
  saveFieldFulfilled,
  saveFieldPending,
  showPublishConfirmationModal,
} from './actions';
import { EntityModel, FieldModel, GraphVersion } from './types';
import { getGraphVersionKey } from './utils';

type ThunkAction<R extends any = void> = BaseThunkAction<R, RootState, unknown, Action<string>>;

export interface GetSchemaForEntityParams {
  entityId: string;
  graphVersion: GraphVersion;
}

export const getSchemaForEntity = ({ entityId, graphVersion }: GetSchemaForEntityParams): ThunkAction => async (
  dispatch
) => {
  dispatch(getEntitySchemaForVersionPending(entityId, graphVersion));
  try {
    const resp = await get(
      replaceToken(DataUrlConstants.ENTITY_FOR_VERSION, {
        syncariEntityId: entityId,
        graphVersion: getGraphVersionKey(graphVersion),
      })
    );
    dispatch(getEntitySchemaForVersionFulfilled(entityId, graphVersion, resp.data));
  } catch (error) {
    dispatch(getEntitySchemaForVersionFailed(entityId, graphVersion, (error as any).toString()));
  }
};

// Same as getSchemaForEntity but pipelines that aren't eligible for quickstarts
// do not return the schema. For example, if they include a custom synapse draft.
export const getSchemaForEntityPipelinePicker = async ({
  entityId,
  graphVersion,
}: GetSchemaForEntityParams): Promise<{ data: any; status: number }> => {
  return await get(
    replaceToken(DataUrlConstants.ENTITY_FOR_VERSION_QS, {
      syncariEntityId: entityId,
      graphVersion: getGraphVersionKey(graphVersion),
    })
  );
};

export const getSchemaForConnector = (connectorId: string): ThunkAction => async (dispatch) => {
  dispatch(getConnectorSchemaPending(connectorId));

  try {
    const resp = await get(replaceToken(DataUrlConstants.GET_SCHEMA_FOR_CONNECTOR, { connectorId }));
    dispatch(getConnectorSchemaFulfilled(connectorId, resp.data));
  } catch (error) {
    dispatch(getConnectorSchemaFailed(connectorId));
  }
};

export const showPublishConfirmationModalForEntityId = (
  entityId: string | null,
  connectorId?: string | null
): ThunkAction => (dispatch) => dispatch(showPublishConfirmationModal(entityId, connectorId));

export const getSchemaForConnectorEntity = (entityId: string): ThunkAction => async (dispatch) => {
  dispatch(getConnectorEntitySchemaPending(entityId));

  try {
    const resp = await get(replaceToken(DataUrlConstants.GET_FIELDS_FOR_ENTITY, { entityId }));
    dispatch(getConnectorEntitySchemaFulfilled(entityId, resp.data));
  } catch (error) {
    dispatch(getConnectorEntitySchemaFailed(entityId, getErrorMessage(error)));
  }
};

export const createDraftForEntity = (entityId: string): ThunkAction<Promise<void>> => async (dispatch) => {
  dispatch(createEntityDraftPending(entityId));

  try {
    await post(replaceToken(DataUrlConstants.CREATE_DRAFT_FOR_ENTITY, { entityId }));
    dispatch(createEntityDraftFulfilled(entityId));
  } catch (err) {
    dispatch(createEntityDraftFailed(entityId, getErrorMessage(err)));
  }
};

// TODO: fix ThunkAction<Promise<any>> typing to be ThunkAction<AxiosPromise<ACTUAL_TYPE>>
export const publishEntitySchema = (entityId: string): ThunkAction<Promise<any>> => async (dispatch) => {
  dispatch(publishEntitySchemaPending(entityId));

  try {
    const resp = await post<any>(replaceToken(DataUrlConstants.PUBLISH_ENTITY_SCHEMA, { entityId }));
    dispatch(publishEntitySchemaFulfilled(entityId, resp?.data));
    dispatch(getEntities());
  } catch (err) {
    dispatch(publishEntitySchemaFailed(entityId, getErrorMessage(err)));
  }
};

export const discardEntitySchema = (entityId: string): ThunkAction<Promise<any>> => async (dispatch) => {
  dispatch(discardEntitySchemaPending(entityId));

  try {
    const resp = await deleteRequest(replaceToken(DataUrlConstants.DISCARD_ENTITY_SCHEMA, { entityId }));
    dispatch(discardEntitySchemaFulfilled(entityId, resp?.data));
  } catch (err) {
    dispatch(discardEntitySchemaFailed(entityId, getErrorMessage(err)));
  }
};

export function saveEntity(
  { id: entityId, ...rest }: Partial<EntityModel>,
  options: { connectorId: string; refresh?: boolean }
): ThunkAction {
  // Note: Move the desctructured optons and entity values up to the params when this file gets converted to ts.
  // Destructuring in param have issues with using this action in a ts component.
  const { refresh, connectorId } = options;

  // if we're creating a new entity, we'll store the status as connetorId-new-entity
  const entityKey = entityId || `${connectorId}-new-entity`;

  return async (dispatch) => {
    let url, method;
    dispatch(saveEntityPending(entityKey));

    if (entityId) {
      url = replaceToken(DataUrlConstants.UPDATE_ENTITY, { entityId });
      method = put;
    } else {
      url = DataUrlConstants.CREATE_ENTITY;
      method = post;
    }

    try {
      const resp = await method(url, rest);
      dispatch(saveEntityFulfilled(entityKey, resp.data));

      if (refresh) {
        dispatch(getSchemaForConnector(connectorId));
      }
    } catch (error) {
      dispatch(saveEntityFailed(entityKey, getErrorMessage(error)));
    }
  };
}

export function saveField(
  fieldValues: FieldModel,
  options: { refresh?: boolean; entityId: string; fieldId: string }
): ThunkAction {
  // Note: Move the desctructured optons up the second param when this file gets converted to ts.
  // Destructuring without ts have issues with using this action in a ts component.
  const { refresh, entityId, fieldId } = options;
  return async (dispatch) => {
    dispatch(saveFieldPending());

    let url, method;
    if (fieldId && entityId) {
      url = replaceToken(DataUrlConstants.UPDATE_FIELD, { entityId, fieldId });
      method = put;
    } else {
      url = replaceToken(DataUrlConstants.CREATE_FIELD, { entityId });
      method = post;
    }

    try {
      const resp = await method(url, fieldValues);
      dispatch(saveFieldFulfilled(resp.data));

      if (refresh) {
        dispatch(getSchemaForConnectorEntity(entityId));
      }
    } catch (error) {
      dispatch(saveFieldFailed(getErrorMessage(error)));
    }
  };
}

export function getEntityDetail(entityId: string): ThunkAction {
  return async (dispatch) => {
    dispatch(getEntityDetailPending(entityId));
    const url = replaceToken(DataUrlConstants.ENTITY_DETAIL, { entityId });
    try {
      const resp = await get(url);
      dispatch(getEntityDetailFulfilled(entityId, resp.data));
    } catch (error) {
      dispatch(getEntityDetailFailed(entityId, getErrorMessage(error)));
    }
  };
}

export interface EntitySchemaParams {
  connectorId: string;
  entityId: string;
}

export const createDraftForEntityAndRefreshConnector = ({
  connectorId,
  entityId,
}: EntitySchemaParams): PromiseThunkAction => (dispatch) =>
  dispatch(createDraftForEntity(entityId)).then(() => dispatch(getSchemaForConnector(connectorId)));

export const publishEntitySchemaAndRefreshConnector = ({ connectorId, entityId }: EntitySchemaParams): ThunkAction => (
  dispatch
) => {
  dispatch(publishEntitySchema(entityId)).then(() => dispatch(getSchemaForConnector(connectorId)));
};

export const discardEntitySchemaAndRefreshConnector = ({
  connectorId,
  entityId,
}: EntitySchemaParams): PromiseThunkAction => (dispatch) =>
  dispatch(discardEntitySchema(entityId)).then(() => dispatch(getSchemaForConnector(connectorId)));
