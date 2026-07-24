import { find, forEach } from 'lodash';

import { SyncStatuses } from 'components/graph/getTagDataForEntityNode';
import { useResyncStates } from 'pages/sync-studio/entity-pipeline/entity-resync/ResyncRequestModal';
import { Entity } from 'store/entity/types';
import AppConstants from 'utils/AppConstants';

import { PipelineStateState, PipelineStatusState } from '../PipelineDetailsFilterPanel';

interface SyncStatus {
  errorCount: number;
  errorDetails: null;
  lagTimeInSeconds: number;
  lastSyncTime: string;
  status: SyncStatuses;
  summary: null;
  syncariEntityId: string;
  warningCount?: number;
}

export interface EntityMetadataType {
  filterStatus: SyncStatuses | 'DRAFT';
  pipelineStatus?: SyncStatuses;
  lastSyncTime?: string | null;
  warningCount?: number;
}

export type EntityMetadataMapType = Record<string, EntityMetadataType>;

export const getEntityMetadataMap = (
  entities?: Entity[],
  entitySyncStatuses?: SyncStatus[],
  resyncStatus?: ReturnType<typeof useResyncStates>
) => {
  const entityStatusMap: EntityMetadataMapType = {};

  if (!entities) {
    return entityStatusMap;
  }

  forEach(entities, (entity) => {
    const entityStatusDetails = find(entitySyncStatuses, { syncariEntityId: entity.id });
    if (entity.pipelineStatus === AppConstants.SYNCARI_NODE_STATUS.DRAFT) {
      entityStatusMap[entity.id] = {
        filterStatus: 'DRAFT',
        pipelineStatus: entityStatusDetails?.status,
        lastSyncTime: entityStatusDetails?.lastSyncTime,
        warningCount: entityStatusDetails?.warningCount,
      };
    } else {
      let pipelineStatus = entityStatusDetails?.status || 'UNPUBLISHED';

      // Override the sync status when cancelling a resync is in progress and
      // pipeline is RUNNING.
      const resyncData = resyncStatus?.resyncDetails?.[entity.id];
      if (
        pipelineStatus === 'RUNNING' &&
        resyncData?.status === 'CANCEL_REQUESTED' &&
        resyncData?.syncStatus === 'RESYNCING'
      ) {
        pipelineStatus = 'RESYNCING';
      }

      // Entities with no draft or published version are not in the entityStatusMap.
      // Adding a status of "UNPUBLISHED" which shows as "Unmapped" on the tag and
      // the pipeline summary view.
      entityStatusMap[entity.id] = {
        filterStatus: entityStatusDetails?.status || 'UNPUBLISHED',
        pipelineStatus,
        lastSyncTime: entityStatusDetails?.lastSyncTime,
        warningCount: entityStatusDetails?.warningCount,
      };
    }
  });

  return entityStatusMap;
};

export const parsePipelineStatus = (searchParam: string) => {
  const status: PipelineStatusState = {
    draft: false,
    published: false,
    unmapped: false,
  };

  searchParam.split(',').forEach((segment) => {
    switch (segment) {
      case 'draft':
        status.draft = true;
        break;
      case 'published':
        status.published = true;
        break;
      case 'unmapped':
        status.unmapped = true;
        break;
    }
  });

  return status;
};

export const parsePipelineState = (searchParam: string) => {
  const state: PipelineStateState = {
    paused: false,
    running: false,
    resyncing: false,
    pausing: false,
    queued: false,
    error: false,
    warning: false,
  };

  searchParam.split(',').forEach((segment) => {
    switch (segment) {
      case 'paused':
        state.paused = true;
        break;
      case 'running':
        state.running = true;
        break;
      case 'resyncing':
        state.resyncing = true;
        break;
      case 'pausing':
        state.pausing = true;
        break;
      case 'queued':
        state.queued = true;
        break;
      case 'error':
        state.error = true;
        break;
      case 'warning':
        state.warning = true;
        break;
    }
  });

  return state;
};

export const parseSourcesOrDestinations = (searchParam: string) => {
  return searchParam.split(',').filter((id) => !!id);
};
