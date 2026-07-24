//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { find, isUndefined } from 'lodash';
import { createSelector } from 'reselect';

import { DirectionId } from 'pages/sync-studio/types';
import { Connector } from 'reducers/connectorReducer';
import { RootState } from 'reducers/index';
import { getDervConnectors, selectAllConnectors, selectConnectorsMetadata } from 'selectors/connectorSelectors';

export const selectFastMapperModalVisible = (state: RootState) => state.fastMapper.fastMapperVisible;
export const selectFastMapperEntityId = (state: RootState) => state.fastMapper.fastMapperEntityId;
export const selectSaveMappingsResponse = (state: RootState) => state.fastMapper.saveMappingsResponse;
export const selectSaveMappingsStatus = (state: RootState) => state.fastMapper.saveMappingsStatus;
export const selectSaveMappingErrorMessage = (state: RootState) => state.fastMapper.saveMappingsErrorMessage;
export const selectServerMappings = (state: RootState) => state.fastMapper.mappings;
export const selectGetMappingsStatus = (state: RootState) => state.fastMapper.mappingsStatus;
export const selectDeleteMappingsResponse = (state: RootState) => state.fastMapper.deleteMappingsResponse;
export const selectConnectorEntities = (state: RootState) => state.entityPipeline.connectorEntities;
export const selectActiveConnectors = (state: RootState) => state.connector.connectors;
export const selectEditMappingsResponse = (state: RootState) => state.fastMapper.editMappingsResponse;
export const selectEditMappingsStatus = (state: RootState) => state.fastMapper.editMappingsStatus;

export const selectMappings = createSelector(
  [selectConnectorsMetadata, selectAllConnectors, selectServerMappings, getDervConnectors],
  (metadata, connectors, mappings, derivedConnectors) => {
    if (metadata?.length && connectors?.length && mappings?.length) {
      return mappings.map((mapping) => {
        const connector = find(connectors, { id: mapping.synapseId });
        const iconUrl = isUndefined(connector)
          ? ''
          : find(metadata, { id: connector.metadataId })?.iconUri ??
            (find(derivedConnectors, { id: connector.connectorId }) as Connector)?.icon ??
            '';

        return {
          ...mapping,
          syncDirectionId: mapping.directions.length > 1 ? DirectionId.BIDIRECTIONAL : mapping.directions?.[0],
          synapseIconUrl: iconUrl,
        };
      });
    }
    return mappings;
  }
);
