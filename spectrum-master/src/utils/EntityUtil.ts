// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { each, find, map, filter, isEmpty } from 'lodash';

import {
  NODE_GRAPH_READY,
  NODE_GRAPH_TEST,
  NODE_GRAPH_SYNCING,
  NODE_GRAPH_PAUSED,
  NODE_GRAPH_WARNING,
  NODE_GRAPH_ERROR,
} from 'components/icons/Icons';
import AppConstants from 'utils/AppConstants';
import { DEFAULT_NODE_LOCATION, DEFAULT_EDGE_ANCHOR } from 'utils/DefaultConstants';
import { t } from 'utils/i18nUtil';

const { SYNC_STATUS } = AppConstants;

export function getResponseEntities(data, connectorId, detailed?: boolean) {
  return {
    entities: data.entities,
    connections: data.connections,
    connectorId,
    detailed,
  };
}

export const getResponseFields = (entityId, entities) => find(entities?.entities, { id: entityId })?.fields;

export function getFieldsWithEntityId(entities) {
  const fields = [];
  if (entities) {
    each(entities, (entity) => {});
  }
  return fields;
}

/**
 * TEMPORARY: The server is not sending back the icon path yet
 */
const ICON_MAP = {
  [AppConstants.SYNCARI_NODE_STATUS.UNMAPPED]: '/assets/icons/unmapped.svg',
  [AppConstants.SYNCARI_NODE_STATUS.PUBLISHED]: '/assets/icons/published.svg',
  [AppConstants.SYNCARI_NODE_STATUS.DRAFT]: '/assets/icons/draft.svg',
  [AppConstants.SYNCARI_NODE_STATUS.PUBLISHED_WITH_DRAFT]: '/assets/icons/published-with-draft.svg',
  [AppConstants.SYNCARI_NODE_STATUS.ERROR]: '/assets/icons/error.svg',
  [AppConstants.SYNCARI_NODE_STATUS.NO_STATUS]: '/assets/icons/syncari-node.svg',
};

function getIconPath(iconPath, status) {
  return ICON_MAP[AppConstants.SYNCARI_NODE_STATUS.NO_STATUS];
}

function getStatusText(status) {
  return t(`SyncStudio.${status}`);
}
function getColorByPipelineStatus(pipelineStatus) {
  switch (pipelineStatus) {
    case AppConstants.SYNCARI_NODE_STATUS.PUBLISHED_WITH_DRAFT:
      return AppConstants.SCHEMA_NODE_COLORS.PUBLISHED_WITH_DRAFT;
    case AppConstants.SYNCARI_NODE_STATUS.PUBLISHED:
      return AppConstants.SCHEMA_NODE_COLORS.PUBLISHED;
    case AppConstants.SYNCARI_NODE_STATUS.DRAFT:
      return AppConstants.SCHEMA_NODE_COLORS.DRAFT;
    case AppConstants.SYNCARI_NODE_STATUS.UNMAPPED:
      return AppConstants.SCHEMA_NODE_COLORS.UNMAPPED;
    case AppConstants.SYNCARI_NODE_STATUS.ERROR:
      return AppConstants.SCHEMA_NODE_COLORS.ERROR;
    default:
      return AppConstants.SCHEMA_NODE_COLORS.NO_STATUS;
  }
}

export function getSyncStatusIcon(syncStatus) {
  switch (syncStatus) {
    case SYNC_STATUS.READY:
      return NODE_GRAPH_READY;
    case SYNC_STATUS.TEST:
      return NODE_GRAPH_TEST;
    case SYNC_STATUS.RUNNING:
    case SYNC_STATUS.RESYNCING:
      return NODE_GRAPH_SYNCING;
    case SYNC_STATUS.PAUSED:
    case SYNC_STATUS.PAUSING:
      return NODE_GRAPH_PAUSED;
    case SYNC_STATUS.STALLED:
      return NODE_GRAPH_WARNING;
    case SYNC_STATUS.ERROR:
      return NODE_GRAPH_ERROR;
    default:
      return '';
  }
}

export function getEntitiesForGraph(entities) {
  const result = [];
  each(entities, (entity) => {
    const { apiName, displayName, id, status } = entity;
    const description = getStatusText(entity.subLabel) || '';
    const label = displayName || apiName;
    const shape = AppConstants.GRAPH_NODE_SHAPES.ENTITY_NODE;

    result.push({
      entityType: entity.type,
      nodeId: entity.id,
      shape,
      label,
      description,
      iconUrl: getIconPath(entity.iconPath, entity.pipelineStatus),
      typeColor: getColorByPipelineStatus(entity.pipelineStatus),
      id,
      syncStatus: entity.syncStatus,
      statusIcon: getSyncStatusIcon(entity.syncStatus),
      warningCount: entity.warningCount,
      metadata: {
        entityType: entity.type,
        nodeId: entity.id,
        nodeName: label,
        apiName,
        displayName,
        pipelineStatus: entity.pipelineStatus,
        id,
        status,
      },
    });
  });
  return result;
}

export function getEntityEdgesForGraph(connections) {
  const result = [];
  each(connections, (connection) => {
    const anchor = connection.anchor || {};
    result.push({
      source: connection.sourceEntityId,
      sourceAnchor: anchor.source,
      targetAnchor: anchor.target,
      target: connection.targetEntityId,
      color: AppConstants.EDGE_COLOR.ENTITY,
      edgeType: AppConstants.EDGE_TYPE.USER_PREFERENCE,
      id: connection.id,
    });
  });
  return result;
}

export function getEntityTags(entities, entityId) {
  let entity;
  if (entities?.length > 0) {
    entity = find(entities, (entity) => entity.id === entityId);
  }
  return entity?.tags ? entity.tags : [];
}

export function getEntityName(entityId, entities) {
  const entity = find(entities, (entity) => {
    return entity.id === entityId;
  });
  if (entity) {
    return entity.displayName;
  }
}

export function getConnectorEntitiesForMapping(connectorEntities, connectorList) {
  if (!connectorEntities) {
    return;
  }
  const entities = map(connectorEntities?.entityMapping, (entities) => {
    return {
      ...entities,
      key: entities.id,
      connectorList,
    };
  });
  return entities;
}

export function getConnectorFieldsForMapping(connectorFields, connectorList, entities) {
  if (!connectorFields) {
    return;
  }
  const fields = map(connectorFields, (fields) => {
    return {
      ...fields,
      key: fields.synapseFieldId,
      connectorList,
      entities,
    };
  });
  return fields;
}

// This is a STRICT matching!!! Be careful of the names
// TODO: Make this a bit loose and forgiving when there are not matches
export function applyDefaults(nodes, edges) {
  if (nodes?.length > 0 && edges?.length > 0) {
    const nameIdMap = {};
    const nodePref = {};
    const edgePref = {};

    // Create a map of id to names
    each(nodes, (node) => {
      nameIdMap[node.apiName.toUpperCase()] = node.id;
    });

    // Save the default values for each node location
    each(nodes, (node) => {
      const name = node.apiName.toUpperCase();
      if (DEFAULT_NODE_LOCATION[name]) {
        nodePref[nameIdMap[name]] = {
          ...DEFAULT_NODE_LOCATION[name],
          id: nameIdMap[node.apiName.toUpperCase()],
        };
      }
    });

    // Save the default preferece value of the edge
    each(DEFAULT_EDGE_ANCHOR, (val, key) => {
      const split = key.split('_');
      const source = nameIdMap[split[0]];
      const target = nameIdMap[split[1]];
      const edgeId = `${source}${target}`;
      edgePref[edgeId] = {
        ...val,
        id: edgeId,
      };
    });
    return {
      nodes: nodePref,
      edges: edgePref,
    };
  }
}

export function areAllUnmappedEntities(entities) {
  return isEmpty(filter(entities, (entity) => entity.pipelineStatus !== AppConstants.SYNCARI_NODE_STATUS.UNMAPPED));
}

// Check for entity validity
export const isValidEntity = (entities, entityId) => entities?.find((entity) => entity.id === entityId);
