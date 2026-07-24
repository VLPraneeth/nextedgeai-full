//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { find } from 'lodash';
import { createSelector } from 'reselect';

import { Connector } from 'reducers/connectorReducer';
import { RootState } from 'reducers/index';
import { getDervConnectors } from 'selectors/connectorSelectors';
import { PANEL_ALLOWED_CONNECTOR_STATUS } from 'utils/ConnectorUtil';

export const selectAllConnectors = (state: RootState) => state.connector.connectors;
export const selectConnectorsMetadata = (state: RootState) => state.connector.connectorsMetadata;

export const selectUserConnectorsForDisplay = createSelector(
  [selectAllConnectors, selectConnectorsMetadata, getDervConnectors],
  (connectors, connectorMetadata, derivedConnectors) =>
    connectors
      ?.filter((connector) => PANEL_ALLOWED_CONNECTOR_STATUS.includes(connector.status as any))
      .map((connector) => {
        const meta = find(connectorMetadata, { id: connector.metadataId });
        const connectorEntity = find(derivedConnectors, { id: connector.connectorId }) as Connector;
        return {
          ...connector,
          iconUri: meta?.iconUri ?? connectorEntity?.icon,
          iconTitle: meta?.displayName ?? connector.name,
        };
      })
);
