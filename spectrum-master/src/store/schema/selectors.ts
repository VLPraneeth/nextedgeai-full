import { createSelector } from 'reselect';

import { RootState } from 'reducers/index';
import { FetchStatus } from 'store/types';
import AppConstants from 'utils/AppConstants';

import { GraphStatus } from './types';
import { makeSchemaKey } from './utils';

export const selectAllConnectorSchemas = (state: RootState) => state.schema.connectorSchemas;
export const selectAllConnectorEntitySchemas = (state: RootState) => state.schema.connectorEntitySchemas;

export const selectSchemaForEntity = (
  state: RootState,
  { entityId, graphVersion = AppConstants.GRAPH_STATUS.APPROVED }: { entityId: string; graphVersion: GraphStatus }
) => {
  return state.schema.entities?.[makeSchemaKey({ entityId, graphVersion })] || {};
};

export const selectSchemaStatusForEntity = (
  state: RootState,
  { entityId, graphVersion = AppConstants.GRAPH_STATUS.APPROVED }: { entityId: string; graphVersion: GraphStatus }
) => state.schema.entitySchemaStatus?.[makeSchemaKey({ entityId, graphVersion })] || AppConstants.FETCH_STATUS.IDLE;

export const selectConnectorSchema = (state: RootState, { connectorId }: { connectorId?: string }) => {
  if (!connectorId) {
    return null;
  }

  return state.schema.connectorSchemas[connectorId];
};

export const selectConnectorSchemaStatus = (
  state: RootState,
  { connectorId }: { connectorId?: string }
): FetchStatus => {
  if (!connectorId) {
    return AppConstants.FETCH_STATUS.IDLE;
  }

  return state.schema.connectorSchemaStatus?.[connectorId] || AppConstants.FETCH_STATUS.IDLE;
};

export const selectEntitySchema = (state: RootState, { entityId }: { entityId?: string }) => {
  if (!entityId) {
    return null;
  }

  return state.schema.connectorEntitySchemas[entityId];
};

export const selectEntitySchemaStatus = (state: RootState, { entityId }: { entityId?: string }): FetchStatus => {
  if (!entityId) {
    return AppConstants.FETCH_STATUS.IDLE;
  }

  return state.schema.connectorEntityStatus?.[entityId] || AppConstants.FETCH_STATUS.IDLE;
};

export const selectShowingPublishConfirmationModal = (state: RootState) =>
  state.schema.showingPublishConfirmationModalMetadata;
export const selectEntitySchemaDraftPublishStatus = (state: RootState) => state.schema.entitySchemaDraftPublishStatus;
export const selectEntitySchemaDraftDiscardStatus = (state: RootState) => state.schema.entitySchemaDraftDiscardStatus;
export const selectEntitySchemaDraftCreateStatus = (state: RootState) => state.schema.entitySchemaDraftCreateStatus;
export const selectEntitySaveStatus = (state: RootState) => state.schema.saveEntityStatus;

export const selectFieldDeleteStatus = createSelector(
  (state: RootState) => state.schemaSlice,
  (schemaSlice) => ({
    deleteStatus: schemaSlice.deletingFieldStatus,
    deleteError: schemaSlice.deletingFieldError,
  })
);
