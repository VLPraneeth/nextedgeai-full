import { find, keyBy } from 'lodash';
import { createSelector } from 'reselect';

import { useEnhancedSelector } from 'hooks/redux';
import { ConnectorCapability, ConnectorMetadata, SchemaRefreshStatus } from 'reducers/connectorReducer';
import { selectAllConnectors } from 'selectors/connectorSelectors';
import { selectConnectors } from 'selectors/pipelineSelectors';
import AppConstants from 'utils/AppConstants';

import { selectConnectorsMetadata } from './selectors';

/*
`isRefreshing` return true while the synapse is refreshing
`unableToUpdate` returns true whenever the synapse is not ready
for actions like "Refresh Schema" or "Active and Create Pipelines"
*/
interface RefreshStatusDetails {
  isRefreshing: boolean;
  unableToUpdate: boolean;
  schemaRefreshStatus: SchemaRefreshStatus;
}

export const useSynapseRefreshingStatus = (connectorId?: string): RefreshStatusDetails => {
  const connectors = useEnhancedSelector((state) => state.connector.connectors);

  const connector = find(connectors, { id: connectorId });

  if (!connector) {
    return {
      isRefreshing: false,
      unableToUpdate: false,
      schemaRefreshStatus: null,
    };
  }

  const { status, schemaRefreshStatus } = connector;

  const isRefreshing = schemaRefreshStatus === AppConstants.SCHEMA_REFRESH_STATUS.PROCESSING;
  const synapseIsActive = status === AppConstants.CONNECTOR_STATUS.ACTIVE;
  const unableToUpdate = isRefreshing || !synapseIsActive;

  return {
    isRefreshing,
    unableToUpdate,
    schemaRefreshStatus,
  };
};

export const useConnectorCapabilities = (
  connectorId?: string
): { capabilities: ConnectorCapability[]; isSyncariConnector: boolean } => {
  const connector = useEnhancedSelector(selectAllConnectors)?.find((connector) => connector.id === connectorId);
  const connectorsMetadata = useEnhancedSelector(selectConnectorsMetadata);

  const metadata = connectorsMetadata?.find((connectorMetadata) => connectorMetadata.id === connector?.metadataId);

  const capabilities = metadata?.capabilities || [];

  return {
    capabilities,
    isSyncariConnector: connector?.name.toLowerCase() === AppConstants.SYNAPSE_NAMES.SYNCARI.toLowerCase() || false,
  };
};

export const useConnectorCanEditSchema = (connectorId?: string): boolean => {
  const { capabilities, isSyncariConnector } = useConnectorCapabilities(connectorId);

  if (isSyncariConnector) {
    return true;
  }

  return capabilities?.includes('schemaEditInSyncari') || false;
};

export const selectConnectorMetadataMap = createSelector(selectConnectorsMetadata, (connectorsMetadata) =>
  keyBy(connectorsMetadata, 'id')
);

export const useConnectorMetadataMap = () => useEnhancedSelector(selectConnectorMetadataMap);

export const selectConnectorIdToMetadataMap = createSelector(
  selectConnectors,
  selectConnectorMetadataMap,
  (connectors, connectorsMetadataMap) => {
    const connectorIdToMetadataMap: Record<string, ConnectorMetadata> = {};

    connectors?.forEach((connector) => {
      connectorIdToMetadataMap[connector.id] = connectorsMetadataMap[connector.metadataId];
    });

    return connectorIdToMetadataMap;
  }
);

export const useConnectorIdToMetadataMap = () => useEnhancedSelector(selectConnectorIdToMetadataMap);
