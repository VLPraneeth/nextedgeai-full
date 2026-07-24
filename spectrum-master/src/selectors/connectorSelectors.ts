// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { map, each, find, sortBy } from 'lodash';
import { createSelector } from 'reselect';

import { Connector } from 'reducers/connectorReducer';
import { RootState } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { addConnectorMetaIcon } from 'utils/ConnectorUtil';
import { tc } from 'utils/i18nUtil';

const getConnectors = (state: RootState) => state.connector.connectors;
export const selectConnectorsMetadata = (state: RootState) => state.connector?.connectorsMetadata;
export const selectConnectorsFetching = (state) => state.connector.fetchingConnectors;
const getCurrentConnectorId = (state: RootState) => state.connector?.connectorId;
const selectOauthRedirectUrl = (state: RootState) => state.connector?.oauthRedirectUrl;

export const selectAllConnectors = getConnectors;
export const selectSyncariConnector = (state: RootState) =>
  getConnectors(state)?.find((connector) => connector.name.toLowerCase() === AppConstants.SYNCARI_CONNECTOR_NAME);

// Create a hash for connectors metadata by id
function hashConnectorsById(connectorsMetadata) {
  const metadata = {};
  each(connectorsMetadata, (meta) => {
    metadata[meta.id] = meta;
  });
  return metadata;
}

export const selectCurrentConnector = createSelector([getCurrentConnectorId, getConnectors], getCurrentConnector);

const SYNCARI_CONNECTOR_METADATA = {
  displayName: 'NextEdge AI',
  icon: '/assets/icons/syncari-square.svg',
};

// TODO: rename to `selectDervConnectors` so that all selectors start with `select`
export const getDervConnectors = createSelector([getConnectors, selectConnectorsMetadata], (connectors, metadata) => {
  if (connectors?.length > 0) {
    const connMeta = hashConnectorsById(addConnectorMetaIcon(metadata));
    const typedConnectors = map(connectors, (connector) => {
      const meta =
        connector.name.toLowerCase() === AppConstants.SYNCARI_CONNECTOR_NAME
          ? SYNCARI_CONNECTOR_METADATA
          : connector?.metadataId in connMeta
          ? connMeta[connector.metadataId]
          : undefined;

      if (meta) {
        return {
          ...connector,
          name: connector.name === AppConstants.SYNCARI_CONNECTOR_NAME ? tc('syncari') : connector.name,
          typeDisplayName: meta?.displayName || tc('syncari'),
          typeName: meta?.displayName || tc('syncari'),
          icon: meta?.icon,
          iconAlt: meta?.displayName || tc('syncari'),
          backgroundColor: meta.backgroundColor,
        };
      }

      return { ...connector, icon: connector.iconUri };
    });

    return sortBy(
      typedConnectors.filter((x) => x !== undefined),
      (connector) => connector.name?.toLowerCase()
    );
  }
  return connectors;
});

function getCurrentConnector(connectorId: string, connectors: Connector[]) {
  return find(connectors, (conn) => conn.id === connectorId);
}

export const selectCurrentOauthRedirectUrl = createSelector(
  [getCurrentConnectorId, getConnectors, selectOauthRedirectUrl],
  (connectorId, connectors, oauthRedirectUrl) => {
    const connector = getCurrentConnector(connectorId, connectors);
    if (oauthRedirectUrl) {
      return oauthRedirectUrl;
    } else {
      return connector?.oauthRedirectUrl;
    }
  }
);
