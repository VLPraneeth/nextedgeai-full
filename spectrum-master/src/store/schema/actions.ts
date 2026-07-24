import {
  SchemaAction,
  GraphVersion,
  EntitySchemaResponse,
  ConnectorSchemaResponse,
  ResponseError,
  EntityDetailsModel,
  GET_ENTITY_SCHEMA_FOR_VERSION_PENDING,
  GET_ENTITY_SCHEMA_FOR_VERSION_FULFILLED,
  GET_ENTITY_SCHEMA_FOR_VERSION_FAILED,
  GET_CONNECTOR_SCHEMA_PENDING,
  GET_CONNECTOR_SCHEMA_FULFILLED,
  GET_CONNECTOR_SCHEMA_FAILED,
  GET_CONNECTOR_ENTITY_SCHEMA_PENDING,
  GET_CONNECTOR_ENTITY_SCHEMA_FULFILLED,
  GET_CONNECTOR_ENTITY_SCHEMA_FAILED,
  CREATE_ENTITY_DRAFT_PENDING,
  CREATE_ENTITY_DRAFT_FULFILLED,
  CREATE_ENTITY_DRAFT_FAILED,
  PUBLISH_ENTITY_SCHEMA_PENDING,
  PUBLISH_ENTITY_SCHEMA_FULFILLED,
  PUBLISH_ENTITY_SCHEMA_FAILED,
  RESET_PUBLISH_ENTITY_SCHEMA,
  DISCARD_ENTITY_SCHEMA_PENDING,
  DISCARD_ENTITY_SCHEMA_FULFILLED,
  DISCARD_ENTITY_SCHEMA_FAILED,
  SHOW_PUBLISH_CONFIRMATION_MODAL,
  SAVE_ENTITY_PENDING,
  SAVE_ENTITY_FULFILLED,
  SAVE_ENTITY_FAILED,
  RESET_ENTITY_MODAL,
  GET_ENTITY_DETAIL_PENDING,
  GET_ENTITY_DETAIL_FULFILLED,
  GET_ENTITY_DETAIL_FAILED,
  SAVE_FIELD_PENDING,
  SAVE_FIELD_FULFILLED,
  SAVE_FIELD_FAILED,
  RESET_FIELD_MODAL,
} from './types';

export const getEntitySchemaForVersionPending = (entityId: string, graphVersion: GraphVersion): SchemaAction => ({
  type: GET_ENTITY_SCHEMA_FOR_VERSION_PENDING,
  payload: { entityId, graphVersion },
});

export const getEntitySchemaForVersionFulfilled = (
  entityId: string,
  graphVersion: GraphVersion,
  data: EntitySchemaResponse
): SchemaAction => ({
  type: GET_ENTITY_SCHEMA_FOR_VERSION_FULFILLED,
  payload: {
    entityId,
    graphVersion,
    data,
  },
});

export const getEntitySchemaForVersionFailed = (
  entityId: string,
  graphVersion: GraphVersion,
  error: string
): SchemaAction => ({
  type: GET_ENTITY_SCHEMA_FOR_VERSION_FAILED,
  payload: {
    entityId,
    graphVersion,
    error,
  },
});

export const getConnectorSchemaPending = (connectorId: string): SchemaAction => ({
  type: GET_CONNECTOR_SCHEMA_PENDING,
  payload: { connectorId },
});

export const getConnectorSchemaFulfilled = (connectorId: string, data: ConnectorSchemaResponse): SchemaAction => ({
  type: GET_CONNECTOR_SCHEMA_FULFILLED,
  payload: {
    connectorId,
    data,
  },
});

export const getConnectorSchemaFailed = (connectorId: string): SchemaAction => ({
  type: GET_CONNECTOR_SCHEMA_FAILED,
  payload: { connectorId },
});

export const showPublishConfirmationModal = (entityId: string | null, connectorId?: string | null): SchemaAction => ({
  type: SHOW_PUBLISH_CONFIRMATION_MODAL,
  payload: {
    entityId,
    connectorId,
  },
});

export const getConnectorEntitySchemaPending = (entityId: string): SchemaAction => ({
  type: GET_CONNECTOR_ENTITY_SCHEMA_PENDING,
  payload: {
    entityId,
  },
});
export const getConnectorEntitySchemaFulfilled = (entityId: string, data: EntitySchemaResponse): SchemaAction => ({
  type: GET_CONNECTOR_ENTITY_SCHEMA_FULFILLED,
  payload: {
    entityId,
    data,
  },
});
export const getConnectorEntitySchemaFailed = (entityId: string, error: ResponseError): SchemaAction => ({
  type: GET_CONNECTOR_ENTITY_SCHEMA_FAILED,
  payload: {
    entityId,
    error,
  },
});

export const createEntityDraftPending = (entityId: string): SchemaAction => ({
  type: CREATE_ENTITY_DRAFT_PENDING,
  payload: {
    entityId,
  },
});

export const createEntityDraftFulfilled = (entityId: string): SchemaAction => ({
  type: CREATE_ENTITY_DRAFT_FULFILLED,
  payload: {
    entityId,
  },
});

export const createEntityDraftFailed = (entityId: string, error: ResponseError): SchemaAction => ({
  type: CREATE_ENTITY_DRAFT_FAILED,
  payload: {
    entityId,
    error,
  },
});

export const publishEntitySchemaPending = (entityId: string): SchemaAction => ({
  type: PUBLISH_ENTITY_SCHEMA_PENDING,
  payload: { entityId },
});

export const publishEntitySchemaFulfilled = (entityId: string, data: any): SchemaAction => ({
  type: PUBLISH_ENTITY_SCHEMA_FULFILLED,
  payload: {
    entityId,
    data,
  },
});

export const publishEntitySchemaFailed = (entityId: string, error: ResponseError): SchemaAction => ({
  type: PUBLISH_ENTITY_SCHEMA_FAILED,
  payload: {
    entityId,
    error,
  },
});

export const resetPublishEntitySchema = (): SchemaAction => ({
  type: RESET_PUBLISH_ENTITY_SCHEMA,
});

export const discardEntitySchemaPending = (entityId: string): SchemaAction => ({
  type: DISCARD_ENTITY_SCHEMA_PENDING,
  payload: { entityId },
});

export const discardEntitySchemaFulfilled = (entityId: string, data: any): SchemaAction => ({
  type: DISCARD_ENTITY_SCHEMA_FULFILLED,
  payload: { entityId, data },
});

export const discardEntitySchemaFailed = (entityId: string, error: ResponseError): SchemaAction => ({
  type: DISCARD_ENTITY_SCHEMA_FAILED,
  payload: { entityId, error },
});

export const saveEntityPending = (entityId: string) => ({
  type: SAVE_ENTITY_PENDING,
  payload: {
    entityId,
  },
});

// TODO: type this!
export const saveEntityFulfilled = (entityId: string, data: any) => ({
  type: SAVE_ENTITY_FULFILLED,
  payload: {
    entityId,
    data,
  },
});
export const saveEntityFailed = (entityId: string, error: ResponseError) => ({
  type: SAVE_ENTITY_FAILED,
  payload: {
    entityId,
    error,
  },
});

export const resetEntityModal = () => ({
  type: RESET_ENTITY_MODAL,
});

export const saveFieldPending = () => ({
  type: SAVE_FIELD_PENDING,
});

// TODO: type this!
export const saveFieldFulfilled = (data: any) => ({
  type: SAVE_FIELD_FULFILLED,
  payload: {
    data,
  },
});
export const saveFieldFailed = (error: ResponseError) => ({
  type: SAVE_FIELD_FAILED,
  payload: {
    error,
  },
});

export function resetFieldModal(): SchemaAction {
  return {
    type: RESET_FIELD_MODAL,
  };
}

export const getEntityDetailPending = (entityId: string): SchemaAction => ({
  type: GET_ENTITY_DETAIL_PENDING,
  payload: { entityId },
});

export const getEntityDetailFulfilled = (entityId: string, data: EntityDetailsModel): SchemaAction => ({
  type: GET_ENTITY_DETAIL_FULFILLED,
  payload: { entityId, data },
});

export const getEntityDetailFailed = (entityId: string, error: ResponseError): SchemaAction => ({
  type: GET_ENTITY_DETAIL_FAILED,
  payload: { entityId, error },
});
