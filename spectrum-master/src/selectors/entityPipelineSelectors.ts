import { keyBy, uniqBy } from 'lodash';
import { createSelector } from 'reselect';

import { NodeModel } from 'components/GraphItemFilter';
import { RootState } from 'reducers/index';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';
import { toTitleCase } from 'utils/StringUtil';

export const selectCurrentEntityPipeline = (state: RootState) => state.entityPipeline?.entityPipeline;
export const selectHasDraft = (state: RootState) => Boolean(state.entityPipeline?.entityPipeline?.draft);

export const selectSourceEntitiesForCurrentEntityPipeline = createSelector(
  [selectCurrentEntityPipeline],
  (entityPipeline) => {
    const nodes = entityPipeline?.draft ? entityPipeline?.draft?.nodes : entityPipeline?.nodes;
    return nodes?.filter((node: any) => node.nodeType === AppConstants.NODE_TYPE.ENTITY_SOURCE) || EMPTY_ARRAY;
  }
);

export const selectSourceEntitiesForCurrentPublishedEntityPipeline = createSelector(
  [selectCurrentEntityPipeline],
  (entityPipeline) =>
    entityPipeline?.nodes?.filter((node: any) => node.nodeType === AppConstants.NODE_TYPE.ENTITY_SOURCE) || EMPTY_ARRAY
);

export const selectSinkEntitiesForCurrentEntityPipeline = createSelector(
  [selectCurrentEntityPipeline],
  (entityPipeline) => {
    const nodes = entityPipeline?.draft ? entityPipeline?.draft?.nodes : entityPipeline?.nodes;
    return nodes?.filter((node: any) => node.nodeType === AppConstants.NODE_TYPE.ENTITY_SINK) || EMPTY_ARRAY;
  }
);

export interface ConnectorDetailsForEntityPipeline {
  label: string;
  connectorId: string;
  sourceEntityId: string;
  disabled: boolean;
  status: string;
  isWebhook?: boolean;
}

export const selectConnectorsForCurrentEntityPipeline = createSelector(
  [(state: RootState) => state.connector.connectors, selectSourceEntitiesForCurrentEntityPipeline],
  (connectors, sourceEntities): ConnectorDetailsForEntityPipeline[] => {
    const connectorMap = keyBy(connectors, 'connectorId');

    const pipelineConnectors: ConnectorDetailsForEntityPipeline[] = sourceEntities.map((entity: NodeModel) => {
      const status = connectorMap[entity.configuration.connectorId]?.status;

      let label = `${entity.subLabel} / ${entity.name} (${entity.apiName})`;
      const disabled = status !== 'ACTIVE';
      if (disabled) {
        label = `${label} (${toTitleCase(status)})`;
      }

      return {
        label,
        connectorId: entity.configuration.connectorId,
        sourceEntityId: entity.configuration.entityDefinition,
        disabled,
        status,
        isWebhook: entity.configuration.webhook,
      };
    });

    return uniqBy(pipelineConnectors, 'sourceEntityId');
  }
);
