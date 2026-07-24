//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { cloneDeep, each, find, keys } from 'lodash';
import { createSelector } from 'reselect';

import { useEnhancedSelector } from 'hooks/redux';
import { useResyncStates } from 'pages/sync-studio/entity-pipeline/entity-resync/ResyncRequestModal';
import { EntityMetadataType, getEntityMetadataMap } from 'pages/sync-studio/entity/PipelineDetails';
import { EMPTY_ARRAY } from 'store/constants';
import { RootState } from 'store/types';
import AppConstants from 'utils/AppConstants';

export const getEntities = (state: RootState) => state.entity.entities;
export const getEntitiesFetching = (state: RootState) => state.entity.entitiesFetching;
const getFieldDraftSummary = (state: RootState) => state.entityPipeline.fieldDraftSummary;

export const selectEntity = (state: RootState, props: { entityId?: string }) =>
  state.entity.entities?.find((entity) => entity.id === props.entityId);
export const selectEntitySyncStatuses = (state: RootState) => state.entityPipeline.entitySyncStatuses || EMPTY_ARRAY;

export const useSelectEntitySyncStatusMap = () => {
  const entities = useEnhancedSelector(getEntities);
  const entitySyncStatuses = useEnhancedSelector(selectEntitySyncStatuses);

  const resyncStatus = useResyncStates();

  return getEntityMetadataMap(entities, entitySyncStatuses, resyncStatus);
};

export const useSyncStatusForEntity = (entityId: string): EntityMetadataType & { pipelineIsPaused: boolean } => {
  const entityStatusMap = useSelectEntitySyncStatusMap();
  const isLoading =
    useEnhancedSelector((state) => state.entityPipeline.getSyncStatusesStatus) === AppConstants.FETCH_STATUS.LOADING;

  const entityStatusDetails = entityStatusMap[entityId];
  const pipelineIsPaused = Boolean(
    entityStatusDetails?.pipelineStatus &&
      (entityStatusDetails.pipelineStatus === AppConstants.SYNC_STATUS.PAUSED ||
        entityStatusDetails.pipelineStatus === AppConstants.SYNC_STATUS.PAUSING)
  );

  return {
    pipelineIsPaused,
    ...entityStatusDetails,
    pipelineStatus: isLoading ? undefined : entityStatusDetails?.pipelineStatus || 'UNPUBLISHED',
  };
};

function findFieldDraftSummary(fieldId: string, fieldDraftSummary: any) {
  const field = find(fieldDraftSummary, (field) => {
    return field.id === fieldId;
  });
  return field;
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
            each(entity.fields, (field) => {
              const fs = findFieldDraftSummary(field.id, fieldDraftSummary[entity.id]);
              if (fs) {
                (field as any).fieldPipelineUpdatedAt = fs.updatedAt;
              }
            });
          }
        });
      }
    }
    return resultEntities;
  }
);

export const selectEntityGraph = createSelector(
  [getEntities, selectEntitySyncStatuses],
  (entities, entitySyncStatuses) => {
    const resultEntities = cloneDeep(entities) || [];
    if (resultEntities?.length > 0 && entitySyncStatuses) {
      resultEntities.map((entity) => {
        const entitySyncStatus = entitySyncStatuses.find(
          (syncStatus: Record<string, string>) => syncStatus.syncariEntityId === entity.id
        );
        if (entitySyncStatus) {
          (entity as any).syncStatus = entitySyncStatus.status;
          (entity as any).warningCount = entitySyncStatus.warningCount;
        }
        return entity;
      });
    }
    return resultEntities;
  }
);
