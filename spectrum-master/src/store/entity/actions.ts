//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import {
  EntityAction,
  GET_ENTITIES_PENDING,
  GET_ENTITIES_FULFILLED,
  GET_ENTITIES_FAILED,
  GET_CONNECTOR_ENTITIES_PENDING,
  GET_CONNECTOR_ENTITIES_FULFILLED,
  GET_CONNECTOR_ENTITIES_FAILED,
  SHOW_CONNECTOR_ENTITY_MODAL,
  SHOW_CONNECTOR_FIELD_MODAL,
  GET_ENTITY_MAPPING_PENDING,
  GET_ENTITY_MAPPING_FULFILLED,
  SAVE_ENTITY_MAPPING_PENDING,
  SAVE_ENTITY_MAPPING_FULFILLED,
  GET_FIELD_MAPPING_PENDING,
  GET_FIELD_MAPPING_FULFILLED,
  SAVE_FIELD_MAPPING_PENDING,
  SAVE_FIELD_MAPPING_FULFILLED,
  SAVE_FIELD_MAPPING_FAILED,
  DELETE_ENTITY_PENDING,
  DELETE_ENTITY_FULFILLED,
  DELETE_ENTITY_FAILED,
  GET_ENTITY_RECORD_COUNTS_PENDING,
  GET_ENTITY_RECORD_COUNTS_FULFILLED,
  GET_ENTITY_RECORD_COUNTS_FAILED,
  GET_ENTITY_FIELDS_PENDING,
  GET_ENTITY_FIELDS_FULFILLED,
  GET_ENTITY_FIELDS_FAILED,
} from './types';

export const getEntitiesPending = (): EntityAction => ({
  type: GET_ENTITIES_PENDING,
});

export const getEntitiesFulfilled = (payload: any): EntityAction => ({
  type: GET_ENTITIES_FULFILLED,
  payload,
});

export const getEntitiesFailed = (error: any): EntityAction => ({
  type: GET_ENTITIES_FAILED,
  error,
});

export const deleteEntityPending = (): EntityAction => ({
  type: DELETE_ENTITY_PENDING,
});

export const deleteEntityFulfilled = (): EntityAction => ({
  type: DELETE_ENTITY_FULFILLED,
});

export const deleteEntityFailed = (error: any): EntityAction => ({
  type: DELETE_ENTITY_FAILED,
  payload: {
    error,
  },
});

interface ConnectorEntityDetails {
  connectorId: string;
  name: string;
}

export const showConnectorEntityModal = (
  visible = true,
  manageConnectorEntity: ConnectorEntityDetails | null = null
): EntityAction => ({
  type: SHOW_CONNECTOR_ENTITY_MODAL,
  visible,
  manageConnectorEntity,
});

export const showConnectorFieldModal = (visible = true, manageConnectorField = null): EntityAction => ({
  type: SHOW_CONNECTOR_FIELD_MODAL,
  visible,
  manageConnectorField,
});

export const getConnectorEntitiesPending = (connectorId: string, detailed?: boolean): EntityAction => ({
  type: GET_CONNECTOR_ENTITIES_PENDING,
  connectorId,
  detailed,
});

export const getConnectorEntitiesFulfilled = (payload: any): EntityAction => ({
  type: GET_CONNECTOR_ENTITIES_FULFILLED,
  payload,
});

export const getConnectorEntitiesFailed = (error: any, connectorId: string, detailed?: boolean): EntityAction => ({
  type: GET_CONNECTOR_ENTITIES_FAILED,
  error,
  connectorId,
  detailed,
});

export const getEntityFieldsPending = (entityId: string): EntityAction => ({
  type: GET_ENTITY_FIELDS_PENDING,
  entityId,
});

export const getEntityFieldsFulfilled = (entityId: string, payload: any[]): EntityAction => ({
  type: GET_ENTITY_FIELDS_FULFILLED,
  payload,
  entityId,
});

export const getEntityFieldsFailed = (entityId: string, error: Record<string, any>): EntityAction => ({
  type: GET_ENTITY_FIELDS_FAILED,
  entityId,
  error,
});

export const getEntityMappingPending = (): EntityAction => ({
  type: GET_ENTITY_MAPPING_PENDING,
});

export const getEntityMappingFulfilled = (payload: any): EntityAction => ({
  type: GET_ENTITY_MAPPING_FULFILLED,
  payload,
});

export const saveEntityMappingPending = (): EntityAction => ({
  type: SAVE_ENTITY_MAPPING_PENDING,
});

export const saveEntityMappingFulfilled = (): EntityAction => ({
  type: SAVE_ENTITY_MAPPING_FULFILLED,
});

export const getFieldMappingPending = () => ({
  type: GET_FIELD_MAPPING_PENDING,
});

export const getFieldMappingFulfilled = (payload: any) => ({
  type: GET_FIELD_MAPPING_FULFILLED,
  payload,
});

export const saveFieldMappingPending = () => ({
  type: SAVE_FIELD_MAPPING_PENDING,
});

export const saveFieldMappingFulfilled = (payload: any) => ({
  type: SAVE_FIELD_MAPPING_FULFILLED,
  payload,
});

export const saveFieldMappingFailed = (payload: any) => ({
  type: SAVE_FIELD_MAPPING_FAILED,
  payload,
});

export const getEntityRecordsCountsPending = (entityApiNames: string[]) => ({
  type: GET_ENTITY_RECORD_COUNTS_PENDING,
  payload: {
    entityApiNames,
  },
});

export const getEntityRecordsCountsFulfilled = (data: Record<string, number>) => ({
  type: GET_ENTITY_RECORD_COUNTS_FULFILLED,
  payload: {
    data,
  },
});

export const getEntityRecordsCountsFailed = (entityApiNames: string[]) => ({
  type: GET_ENTITY_RECORD_COUNTS_FAILED,
  payload: {
    entityApiNames,
  },
});

export * from 'store/entity/thunks';
