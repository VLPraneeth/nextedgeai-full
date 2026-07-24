//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { find } from 'lodash';
import { createSelector } from 'reselect';

import { RootState } from 'reducers/index';
import { selectConnectorsMetadata, selectAllConnectors } from 'selectors/connectorSelectors';

import { AttributeNode } from './types';

export const selectAttributeNodes = (state: RootState) => state.fieldPipeline.attributeNodes;

export const selectAttributeNodesWithMeta = createSelector(
  [selectAttributeNodes, selectConnectorsMetadata, selectAllConnectors],
  (attributeNodes, connectorMetadata, connectors) => {
    return attributeNodes?.map((attr: AttributeNode) => {
      const connector = find(connectors, { id: attr.connectorId });
      if (connector) {
        const meta = find(connectorMetadata, { id: connector.metadataId });
        if (meta) {
          return {
            ...attr,
            backgroundColor: meta.backgroundColor,
          };
        }
      }
      return attr;
    });
  }
);
