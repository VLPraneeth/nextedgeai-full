//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { cloneDeep, each, find, includes, keys, map } from 'lodash';
import { createSelector } from 'reselect';

import { useEnhancedSelector } from 'hooks/redux';
import AppConstants from 'utils/AppConstants';

import { RootState } from '../../reducers';
import { Entity, EntityStatus } from './types';

export const publishedEntityStatuses: EntityStatus[] = [
  AppConstants.SYNCARI_NODE_STATUS.PUBLISHED,
  AppConstants.SYNCARI_NODE_STATUS.PUBLISHED_WITH_DRAFT,
] as const;

export const getEntityState = (state: RootState) => state.entity;
export const getEntities = (state: RootState) => state.entity.entities;
export const selectPublishedEntities = (state: RootState): Entity[] | undefined => {
  return getEntities(state)?.filter((entity) => includes(publishedEntityStatuses, entity.pipelineStatus));
};
export const getEntitiesFetching = (state: RootState) => state.entity.entitiesFetching;
const getFieldDraftSummary = (state: RootState) => state.entityPipeline.fieldDraftSummary;

interface SelectEntityProps {
  entityId: string;
}
export const selectEntityById = (state: RootState, entityId: string) =>
  state.entity.entities?.find((entity) => entity.id === entityId);

export const useEntity = (entityId: string) => useEnhancedSelector((state) => selectEntityById(state, entityId));

// legacy version
export const selectEntity = (state: RootState, props: SelectEntityProps) => selectEntityById(state, props.entityId);

export const selectConnectorEntitiesOnly = (state: RootState) => state.entity.connectorEntitiesOnly;
export const selectConnectorFields = (state: RootState) => state.entity.connectorFieldsWithStatus;
export const selectAllEntities = getEntities;

// TODO: better types
function findFieldDraftSummary(fieldId: string, fieldDraftSummary: any) {
  return find(fieldDraftSummary, (field) => field.id === fieldId);
}

// TODO: rename to `selectDervEntitiesWithFieldDraftSummary` so that all selectors start with `select`
export const getDervEntitiesWithFieldDraftSummary = createSelector(
  [getEntities, getFieldDraftSummary],
  (entities, fieldDraftSummary) => {
    const resultEntities = cloneDeep(entities) || [];
    if (resultEntities?.length > 0) {
      const summaryKeys = keys(fieldDraftSummary);
      if (summaryKeys?.length > 0) {
        each(resultEntities, (entity) => {
          if (summaryKeys.indexOf(entity.id) !== -1) {
            each(entity.fields, (field: any) => {
              const fs = findFieldDraftSummary(field.id, fieldDraftSummary[entity.id]);
              if (fs) {
                field.fieldPipelineUpdatedAt = fs.updatedAt;
              }
            });
          }
        });
      }
    }
    return resultEntities;
  }
);

export const selectConnectorEntitiesForMapping = createSelector(
  [(state: RootState) => state.entity, (state: RootState) => state.connector],
  (entity, connector) => {
    const connectorId = entity.manageConnectorEntity?.connectorId;
    const connectorEntities = entity.connectorEntities;
    const connectors = connector.connectors;
    const connectorList = connectors.filter((c) => c.connectorId !== connectorId);

    return map(connectorEntities?.entityMapping, (entities) => {
      return {
        ...entities,
        key: entities.id,
        connectorList,
      };
    });
  }
);

// Entity map from apiName -> Entity
export const selectEntityApiNameMap = createSelector([getEntities], (entities) => {
  if (!entities) {
    return {};
  }

  return entities.reduce((acc, entity) => {
    acc[entity.apiName] = entity;
    return acc;
  }, {} as Record<Entity['apiName'], Entity>);
});
