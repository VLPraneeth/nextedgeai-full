import produce, { Draft } from 'immer';
import { cloneDeep } from 'lodash';

import { ActionTypes as FieldPipelineActionTypes } from 'actions/fieldPipelineActions';
import AppConstants from 'utils/AppConstants';

import { deleteSchemaField } from './slice';
import {
  CREATE_ENTITY_DRAFT_FAILED,
  CREATE_ENTITY_DRAFT_FULFILLED,
  CREATE_ENTITY_DRAFT_PENDING,
  DISCARD_ENTITY_SCHEMA_FAILED,
  DISCARD_ENTITY_SCHEMA_FULFILLED,
  DISCARD_ENTITY_SCHEMA_PENDING,
  GET_CONNECTOR_ENTITY_SCHEMA_FAILED,
  GET_CONNECTOR_ENTITY_SCHEMA_FULFILLED,
  GET_CONNECTOR_ENTITY_SCHEMA_PENDING,
  GET_CONNECTOR_SCHEMA_FAILED,
  GET_CONNECTOR_SCHEMA_FULFILLED,
  GET_CONNECTOR_SCHEMA_PENDING,
  GET_ENTITY_DETAIL_FAILED,
  GET_ENTITY_DETAIL_FULFILLED,
  GET_ENTITY_DETAIL_PENDING,
  GET_ENTITY_SCHEMA_FOR_VERSION_FAILED,
  GET_ENTITY_SCHEMA_FOR_VERSION_FULFILLED,
  GET_ENTITY_SCHEMA_FOR_VERSION_PENDING,
  LegacySchemaState,
  PUBLISH_ENTITY_SCHEMA_FAILED,
  PUBLISH_ENTITY_SCHEMA_FULFILLED,
  PUBLISH_ENTITY_SCHEMA_PENDING,
  RESET_ENTITY_MODAL,
  RESET_FIELD_MODAL,
  RESET_PUBLISH_ENTITY_SCHEMA,
  SAVE_ENTITY_FAILED,
  SAVE_ENTITY_FULFILLED,
  SAVE_ENTITY_PENDING,
  SAVE_FIELD_FAILED,
  SAVE_FIELD_FULFILLED,
  SAVE_FIELD_PENDING,
  SchemaAction,
  SHOW_PUBLISH_CONFIRMATION_MODAL,
} from './types';
import { makeSchemaKey, SCHEMA_STATUSES_MAP } from './utils';

const { FETCH_STATUS } = AppConstants;

const { MARK_FIELD_PIPELINE_READY_FULFILLED, MARK_FIELD_PIPELINE_NOT_READY_FULFILLED } = FieldPipelineActionTypes;

function markFieldReady(draft: Draft<LegacySchemaState>, action: SchemaAction, ready: boolean = true) {
  // @ts-ignore
  const schemaKey = makeSchemaKey({ entityId: action.entityId, graphVersion: SCHEMA_STATUSES_MAP.NEW });
  const entities = cloneDeep(draft.entities);
  const field = entities[schemaKey]?.fields?.find((field: any) => {
    // @ts-ignore
    return field.id === action.fieldId;
  });
  if (field) {
    field.ready = ready;
    // @ts-ignore
    field.isMapped = action.isMapped ?? true;
    return entities;
  } else {
    return draft.entities;
  }
}

export const initialState: LegacySchemaState = {
  // entities of shape { [entityId-graphVersion]: Schema }
  entities: {},
  // entitySchemaStatus of shape { [entityId-graphVersion]: FETCH_STATUS }
  entitySchemaStatus: {},

  // shape: { [connectorId]: { draft: Schema, published: Schema } }
  connectorSchemas: {},
  // shape: { [connectorId]: FETCH_STATUS }
  connectorSchemaStatus: {},

  // shape: { [entityId]: { } }
  connectorEntitySchemas: {},

  // shape: { [entityId]: FETCH_STATUS }
  connectorEntityStatus: {},

  entityDetails: null,
  getEntityDetailStatus: FETCH_STATUS.IDLE,
  getEntityDetailErrorMessage: '',
  saveEntityStatus: FETCH_STATUS.IDLE,
  saveEntityErrorMessage: '',
  saveFieldStatus: FETCH_STATUS.IDLE,
  saveFieldErrorMessage: '',
  entitySchemaDraftPublishStatus: FETCH_STATUS.IDLE,
  entitySchemaDraftDiscardStatus: FETCH_STATUS.IDLE,
  entitySchemaDraftCreateStatus: FETCH_STATUS.IDLE,

  // null | string
  showingPublishConfirmationModalForEntityId: null,
  showingPublishConfirmationModalMetadata: null,
};

const reducer = produce((draft: Draft<LegacySchemaState>, action: SchemaAction) => {
  switch (action.type) {
    case GET_ENTITY_SCHEMA_FOR_VERSION_PENDING: {
      const schemaKey = makeSchemaKey(action.payload);
      draft.entitySchemaStatus[schemaKey] = FETCH_STATUS.LOADING;
      break;
    }
    case GET_ENTITY_SCHEMA_FOR_VERSION_FULFILLED: {
      const schemaKey = makeSchemaKey(action.payload);
      draft.entitySchemaStatus[schemaKey] = FETCH_STATUS.SUCCESS;
      // @ts-ignore
      draft.entities[schemaKey] = action.payload.data;
      break;
    }
    case GET_ENTITY_SCHEMA_FOR_VERSION_FAILED: {
      const schemaKey = makeSchemaKey(action.payload);
      draft.entitySchemaStatus[schemaKey] = FETCH_STATUS.ERROR;
      break;
    }
    case GET_CONNECTOR_SCHEMA_PENDING: {
      const { connectorId } = action.payload;
      draft.connectorSchemaStatus[connectorId] = FETCH_STATUS.LOADING;
      break;
    }
    case GET_CONNECTOR_SCHEMA_FULFILLED: {
      const { connectorId, data } = action.payload;
      draft.connectorSchemas[connectorId] = data;
      draft.connectorSchemaStatus[connectorId] = FETCH_STATUS.SUCCESS;
      break;
    }
    case GET_CONNECTOR_SCHEMA_FAILED: {
      const { connectorId } = action.payload;
      draft.connectorSchemaStatus[connectorId] = FETCH_STATUS.ERROR;
      break;
    }
    case GET_CONNECTOR_ENTITY_SCHEMA_PENDING: {
      const { entityId } = action.payload;
      draft.connectorEntityStatus[entityId] = FETCH_STATUS.LOADING;
      break;
    }
    case GET_CONNECTOR_ENTITY_SCHEMA_FULFILLED: {
      const { entityId, data } = action.payload;
      draft.connectorEntitySchemas[entityId] = data;
      draft.connectorEntityStatus[entityId] = FETCH_STATUS.SUCCESS;
      break;
    }
    case GET_CONNECTOR_ENTITY_SCHEMA_FAILED: {
      const { entityId } = action.payload;
      draft.connectorEntityStatus[entityId] = FETCH_STATUS.ERROR;
      break;
    }
    case GET_ENTITY_DETAIL_PENDING: {
      draft.getEntityDetailStatus = FETCH_STATUS.LOADING;
      draft.getEntityDetailErrorMessage = '';
      break;
    }
    case GET_ENTITY_DETAIL_FULFILLED: {
      draft.getEntityDetailStatus = FETCH_STATUS.SUCCESS;
      draft.entityDetails = action.payload.data;
      break;
    }
    case GET_ENTITY_DETAIL_FAILED: {
      draft.getEntityDetailStatus = FETCH_STATUS.ERROR;
      draft.getEntityDetailErrorMessage = action.payload.error;
      break;
    }
    case SAVE_ENTITY_PENDING: {
      draft.saveEntityStatus = FETCH_STATUS.LOADING;
      draft.saveEntityErrorMessage = '';
      break;
    }
    case SAVE_ENTITY_FULFILLED: {
      draft.entityDetails = action.payload.data;
      draft.saveEntityStatus = FETCH_STATUS.SUCCESS;
      break;
    }
    case SAVE_ENTITY_FAILED: {
      draft.saveEntityStatus = FETCH_STATUS.ERROR;
      draft.saveEntityErrorMessage =
        'errorMessage' in action.payload.error ? action.payload.error.errorMessage : action.payload.error.toString();
      break;
    }
    case RESET_ENTITY_MODAL:
      draft.saveEntityStatus = FETCH_STATUS.IDLE;
      draft.saveEntityErrorMessage = '';
      break;
    case SAVE_FIELD_PENDING: {
      draft.saveFieldStatus = FETCH_STATUS.LOADING;
      draft.saveFieldErrorMessage = '';
      break;
    }
    case SAVE_FIELD_FULFILLED: {
      draft.saveFieldStatus = FETCH_STATUS.SUCCESS;
      break;
    }
    case SAVE_FIELD_FAILED:
      draft.saveFieldStatus = FETCH_STATUS.ERROR;
      draft.saveFieldErrorMessage =
        'errorMessage' in action.payload.error ? action.payload.error.errorMessage : action.payload.error.toString();
      break;
    case RESET_FIELD_MODAL:
      draft.saveFieldStatus = FETCH_STATUS.IDLE;
      draft.saveFieldErrorMessage = '';
      break;
    case DISCARD_ENTITY_SCHEMA_PENDING:
      draft.entitySchemaDraftDiscardStatus = FETCH_STATUS.LOADING;
      break;
    case DISCARD_ENTITY_SCHEMA_FULFILLED:
      draft.entitySchemaDraftDiscardStatus = FETCH_STATUS.SUCCESS;
      break;
    case DISCARD_ENTITY_SCHEMA_FAILED:
      draft.entitySchemaDraftDiscardStatus = FETCH_STATUS.ERROR;
      break;
    case CREATE_ENTITY_DRAFT_PENDING:
      draft.entitySchemaDraftCreateStatus = FETCH_STATUS.LOADING;
      break;
    case CREATE_ENTITY_DRAFT_FULFILLED:
      draft.entitySchemaDraftCreateStatus = FETCH_STATUS.SUCCESS;
      break;
    case CREATE_ENTITY_DRAFT_FAILED:
      draft.entitySchemaDraftCreateStatus = FETCH_STATUS.ERROR;
      break;
    case PUBLISH_ENTITY_SCHEMA_PENDING:
      draft.entitySchemaDraftPublishStatus = FETCH_STATUS.LOADING;
      draft.entitySchemaDraftPublishErrorMessage = '';
      break;
    case PUBLISH_ENTITY_SCHEMA_FULFILLED:
      draft.entitySchemaDraftPublishStatus = FETCH_STATUS.SUCCESS;
      draft.entitySchemaDraftPublishErrorMessage = '';
      break;
    case PUBLISH_ENTITY_SCHEMA_FAILED:
      draft.entitySchemaDraftPublishStatus = FETCH_STATUS.ERROR;
      draft.entitySchemaDraftPublishErrorMessage = action?.payload?.error?.message;
      break;
    case RESET_PUBLISH_ENTITY_SCHEMA:
      draft.entitySchemaDraftPublishStatus = FETCH_STATUS.IDLE;
      draft.entitySchemaDraftPublishErrorMessage = '';
      break;
    case SHOW_PUBLISH_CONFIRMATION_MODAL:
      draft.showingPublishConfirmationModalMetadata = action.payload;
      draft.entitySchemaDraftPublishStatus = FETCH_STATUS.IDLE;
      break;
    case MARK_FIELD_PIPELINE_READY_FULFILLED:
      draft.entities = markFieldReady(draft, action);
      break;
    case MARK_FIELD_PIPELINE_NOT_READY_FULFILLED:
      draft.entities = markFieldReady(draft, action, false);
      break;

    // Once all the reducer code is moved to the slice.ts file we can simply
    // move this logic to the deleteSchemaField.pending case.
    case deleteSchemaField.fulfilled.toString():
      const { entityId, fieldId } = (action as any).meta.arg;

      const entity = draft.connectorEntitySchemas[entityId];
      entity.data.forEach((field, index) => {
        if (field.published?.fields.id === fieldId || field.draft?.fields.id === fieldId) {
          delete entity.data[index];
        }
      });
      break;
    default:
      return draft;
  }
}, initialState);

export default reducer;
