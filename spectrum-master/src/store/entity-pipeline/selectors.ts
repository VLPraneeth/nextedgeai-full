//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { clone } from 'lodash';
import { find } from 'lodash';
import { createSelector } from 'reselect';

import { RootState } from 'reducers/index';
import { selectConnectorsMetadata, selectAllConnectors } from 'selectors/connectorSelectors';

import { ConnectorEntityNode } from './types';

export const selectEntitySyncStatus = (state: RootState) => state.entityPipeline?.entitySyncStatus;
export const selectCurrentEntitySyncStatus = (state: RootState) => state.entityPipeline?.entityPipeline?.syncStatus;
export const selectResyncDetails = (state: RootState) => state.entityPipeline?.resyncDetails;
export const selectConnectorEntities = (state: RootState) => state.entityPipeline.connectorEntities;

export const selectSyncStatus = createSelector([selectEntitySyncStatus], (syncStatus) => {
  if (syncStatus) {
    const {
      errorCount,
      errorDetails,
      lagTimeInSeconds,
      lastSyncTime,
      status,
      syncariEntityId,
      warningCount,
    } = syncStatus;
    return { errorCount, errorDetails, lagTimeInSeconds, lastSyncTime, status, syncariEntityId, warningCount };
  }
});

export const selectSourcesSyncStatus = createSelector(
  [selectEntitySyncStatus, selectConnectorsMetadata],
  (syncStatus, connectorMetadata) => {
    return (
      connectorMetadata &&
      syncStatus?.summary?.sources?.map((connectorSyncStatus: any) => {
        const meta = connectorMetadata.find((m: any) => m.name === connectorSyncStatus.connectorType);
        if (meta) {
          return { ...clone(connectorSyncStatus), iconPath: meta.iconUri, connectorTypeDisplayName: meta.displayName };
        } else {
          return connectorSyncStatus;
        }
      })
    );
  }
);

export const selectDestinationSyncStatus = createSelector(
  [selectEntitySyncStatus, selectConnectorsMetadata],
  (syncStatus, connectorMetadata) => {
    return (
      connectorMetadata &&
      syncStatus?.summary?.sinks?.map((connectorSyncStatus: any) => {
        const meta = connectorMetadata.find((m: any) => m.name === connectorSyncStatus.connectorType);
        if (meta) {
          return { ...clone(connectorSyncStatus), iconPath: meta.iconUri, connectorTypeDisplayName: meta.displayName };
        } else {
          return connectorSyncStatus;
        }
      })
    );
  }
);

export const selectConnectorEntitiesWithMeta = createSelector(
  [selectConnectorEntities, selectConnectorsMetadata, selectAllConnectors],
  (connectorEntities, connectorMetadata, connectors) => {
    return connectorEntities.map((connectorEntity: ConnectorEntityNode) => {
      const connectorId = find(connectorEntity.configuration, { name: 'connectorId' });
      if (connectorId?.value) {
        const connector = find(connectors, { id: connectorId.value });
        if (connector) {
          const meta = find(connectorMetadata, { id: connector.metadataId });
          if (meta) {
            return {
              ...connectorEntity,
              backgroundColor: meta.backgroundColor,
              status: connector.status,
              custom: meta.custom,
              draftStatus: meta.draftStatus,
            };
          }
        }
      }
      return connectorEntity;
    });
  }
);

export const selectUserPipelineViewportMatrices = createSelector([(state: RootState) => state.user], (user) => {
  return user.userPref?.syncStudio?.pipelineViewports;
});
