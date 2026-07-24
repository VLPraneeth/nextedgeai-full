// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { navigate } from '@reach/router';
import ObjectID from 'bson-objectid';
import produce from 'immer';
import { cloneDeep, cloneDeepWith, each, find, isEmpty, isUndefined } from 'lodash';

import { ICON_MAP, ICON_TYPE } from 'components/icons/Icons';
import { NON_UNIQUE_NODE_SHAPES } from 'pages/sync-studio/pipeline/PipelineEditor.constants';
import AppConstants from 'utils/AppConstants';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/UrlUtil';

import { connectorIsCustomDraft } from './ConnectorUtil';
import { getNodeShape } from './GraphUtil';
import { findNode, handleChangedNodeGroup, validateChangedNodeGroup } from './Pipeline.utils';
import { generateUniqueNamesCallback } from './StringUtil';

const DataProp = {
  DEFAULT_X: 600,
  DEFAULT_Y: 400,
};

const DEFAULT_BACKGROUND_COLOR = '#FFFFFF';

const META_CONFIGURATION_NODE_TYPE = [AppConstants.NODE_TYPE.FUNCTION, AppConstants.NODE_TYPE.ACTION];

const { GRAPH_STATUS } = AppConstants;

export function getNodesForPipelineGraph(nodes) {
  return nodes.map((node) => {
    const { x = DataProp.DEFAULT_X, y = DataProp.DEFAULT_Y } = node.location;

    return {
      ...node,
      displayName: node.label || node.name,
      description: node.subLabel || '',
      nodeType: node.nodeType,
      location: {
        x,
        y,
      },
    };
  });
}

export function getEdgesForPipelineGraph(edges) {
  return edges.map((edge) => ({
    ...edge,
    sourceEntityId: edge.source.nodeId,
    targetEntityId: edge.destination.nodeId,
  }));
}

function getNodeTypeColor(node, metadata) {
  let color;
  switch (node.nodeType) {
    case AppConstants.NODE_TYPE.ENTITY_SINK:
    case AppConstants.NODE_TYPE.ENTITY_SOURCE: {
      const connectorEntity = find(metadata.connectorEntities, { id: node?.configuration?.configId });
      if (connectorEntity && node?.apiName !== 'timeTicker') {
        color = connectorEntity.backgroundColor;
      } else {
        color = DEFAULT_BACKGROUND_COLOR;
      }
      break;
    }
    case AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE:
    case AppConstants.NODE_TYPE.ATTRIBUTE_SINK: {
      const attr = find(metadata.attributeNodes, { id: node?.configuration?.configId });
      if (attr && attr?.name !== 'timeTicker') {
        color = attr.backgroundColor;
      } else {
        color = DEFAULT_BACKGROUND_COLOR;
      }
      break;
    }

    case AppConstants.NODE_TYPE.FUNCTION:
      color = AppConstants.FLOW_TYPE_COLOR.FUNCTION;
      break;
    case AppConstants.NODE_TYPE.ACTION:
      color = AppConstants.FLOW_TYPE_COLOR.ACTION;
      break;
    case AppConstants.NODE_TYPE.CORE_ENTITY:
    case AppConstants.NODE_TYPE.CORE_ATTRIBUTE: {
      const attr = find(metadata.attributeNodes, { id: node?.configuration?.configId });
      if (attr && attr?.backgroundColor) {
        color = attr.backgroundColor;
      } else {
        color = AppConstants.FLOW_TYPE_NODE_COLOR.SYNCARI;
      }
      break;
    }
    default:
      color = AppConstants.FLOW_TYPE_COLOR.DEFAULT;
      break;
  }
  return color;
}

export function getNodeIcon(node, data = {}) {
  let icon, configList;

  switch (node.nodeType) {
    case AppConstants.NODE_TYPE.ENTITY_SINK:
    case AppConstants.NODE_TYPE.ENTITY_SOURCE:
    case AppConstants.NODE_TYPE.CONNECTOR_ENTITY:
      if (node?.configuration?.configId) {
        configList = data.connectorEntities;
      }
      break;
    case AppConstants.NODE_TYPE.ATTRIBUTE_SINK:
    case AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE:
    case AppConstants.NODE_TYPE.CONNECTOR_ATTRIBUTE:
      if (node?.configuration?.configId) {
        configList = data.attributeNodes;
      }
      break;
    case AppConstants.NODE_TYPE.FUNCTION:
      if (node?.configuration?.configId) {
        configList = data.pipelineFunctions;
      }
      break;
    case AppConstants.NODE_TYPE.ACTION:
      if (node?.configuration?.configId) {
        configList = data.pipelineActions;
      }
      break;
    case AppConstants.NODE_TYPE.CORE_ENTITY:
    case AppConstants.NODE_TYPE.CORE_ATTRIBUTE:
      icon = ICON_MAP[ICON_TYPE.SYNCARI_NO_STATUS];
      break;
    default:
      break;
  }
  if (!icon && configList) {
    const con = find(configList, (conf) => {
      return conf.id === node?.configuration?.configId;
    });

    if (con) {
      icon = con.iconPath;
    }
  }

  return icon;
}

export function shouldHideLeftStrip(node) {
  const hiddenLeftStripNodeTypes = [
    AppConstants.NODE_TYPE.ENTITY_SINK,
    AppConstants.NODE_TYPE.ENTITY_SOURCE,
    AppConstants.NODE_TYPE.ATTRIBUTE_SINK,
    AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE,
    AppConstants.NODE_TYPE.CONNECTOR_ENTITY,
    AppConstants.NODE_TYPE.CONNECTOR_ATTRIBUTE,
  ];
  return hiddenLeftStripNodeTypes.indexOf(node.nodeType) !== -1;
}

export function getNodesForGraph(nodes, data) {
  const predicateNode = find(data.pipelineFunctions, { name: AppConstants.PREDICATE_FUNCTION_NAME });
  const forEachNode = find(data.pipelineFunctions, { name: AppConstants.FOR_EACH_FUNCTION_NAME });
  const endLoopNode = find(data.pipelineFunctions, { name: AppConstants.END_LOOP_FUNCTION_NAME });

  return nodes.map((node) => {
    const { apiName, displayName, id, status, description, groupId } = node;
    const label = displayName || apiName;

    // Remove this default when the backend support location
    const location = node.location || {
      x: AppConstants.GRAPH_LOCATION.CENTER_X,
      y: AppConstants.GRAPH_LOCATION.CENTER_X,
    };
    const icon = getNodeIcon(node, data);
    if (!icon) {
      console.warn('Blank icon! node: ', cloneDeep(node), ' data: ', cloneDeep(data));
    }

    const caseBranch = find(data.pipelineFunctions, { name: AppConstants.CASE_BRANCH_FUNCTION_NAME });

    const nodeIsFunction = node.nodeType === AppConstants.NODE_TYPE.FUNCTION;

    const nodeIsPredicate = nodeIsFunction && predicateNode?.id && predicateNode.id === node.configuration.configId;

    const nodeIsBranch = nodeIsFunction && caseBranch?.id && caseBranch.id === node.configuration.configId;

    const nodeIsForEach = forEachNode?.id && forEachNode.id === node.configuration.configId;
    const nodeIsEndLoop = endLoopNode?.id && endLoopNode.id === node.configuration.configId;
    const nodeIsLoopSidekick = nodeIsFunction && (nodeIsForEach || nodeIsEndLoop);

    const connectorMetadata = data.connectorIdToMetadataMap[node.configuration.connectorId];
    const customSynapseDraft = connectorIsCustomDraft(connectorMetadata);

    let shape = getNodeShape(node.nodeType, node.configuration.configId);
    if (nodeIsPredicate) {
      shape = AppConstants.GRAPH_NODE_SHAPES.PREDICATE_FUNCTION;
    } else if (nodeIsBranch) {
      shape = AppConstants.GRAPH_NODE_SHAPES.CASE_BRANCH_FUNCTION;
    } else if (nodeIsLoopSidekick) {
      shape = AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION;
    }

    return {
      shape,
      label,
      icon,
      description,
      typeColor: getNodeTypeColor(node, data),
      customSynapseDraft,
      nodeType: node.nodeType,
      parent: groupId,
      id,
      x: Number(location.x),
      y: Number(location.y),
      // metadata is specific to rendering the graph
      // configuration is for backend
      metadata: {
        ...node,
        nodeType: node.nodeType,
        nodeId: node.id,
        nodeName: label,
        apiName,
        displayName,
        id,
        status,
      },
    };
  });
}

export function getEdgesForGraph(edges) {
  return edges.map((edge) => {
    const key = edge.key;
    const anchor = edge.anchor || {
      source: Number(edge.source?.anchor || 2),
      target: Number(edge.destination?.anchor || 0),
    };

    return {
      key,
      source: edge?.source?.nodeId || edge.sourceEntityId,
      sourceAnchor: anchor.source,
      targetAnchor: anchor.target,
      color: AppConstants.EDGE_COLOR.PIPELINE,
      target: edge?.destination?.nodeId || edge.targetEntityId,
      id: edge.id,
    };
  });
}

export function createDraftGraph(nodes, edges) {
  const ids = {};
  const validIdKeys = ['nodeId', 'id'];

  return cloneDeepWith({ nodes, edges }, (value, key) => {
    if (validIdKeys.indexOf(key) !== -1) {
      if (!ids[value]) {
        ids[value] = ObjectID.generate();
        return ids[value];
      } else {
        return ids[value];
      }
    }
  });
}

export interface UpdateGraphProps {
  nodes: null | any[];
  edges: null | any[];
  groups?: null | any[];
  event: any;
}

const { NODE_ACTION } = AppConstants;

/**
 * Updates the current nodes and edges with the change triggerd from the g6 editor
 */
export function updateGraph({ nodes, edges, groups, event }: UpdateGraphProps): any {
  let newNodes = cloneDeep(nodes);
  let newEdges = cloneDeep(edges);
  let newGroups = cloneDeep(groups);
  let nodeGroupValidationMessage = '';

  if (event.action === NODE_ACTION.ADD) {
    if (event.item.type === 'node') {
      newNodes = createNode(newNodes, event.model);
    } else if (event.item.type === 'edge') {
      newEdges = createEdge(newEdges, event.model, event.item.source, event.item.target);
    } else if (event.item.type === 'group') {
      newGroups = createGroup(newGroups, event.model);
    }
  } else if (event.action === NODE_ACTION.REMOVE) {
    if (event.item.type === 'node') {
      newNodes = removeNode(nodes, edges, event.item.id);
      newEdges = removeNodeEdges(edges, event.item.id);
      handleChangedNodeGroup(newNodes, event);
    } else if (event.item.type === 'edge') {
      newEdges = removeEdge(edges, event.item.id);
    }
  } else if (event.action === NODE_ACTION.CHANGE_DATA) {
    // noop
  } else if (event.action === NODE_ACTION.UPDATE_CONFIG) {
    let node = findNode(newNodes, event.originModel.id);
    if (node) {
      const { nodeType, label, outputPorts, inputPorts, configuration = {} } = event.updateModel;
      if (!nodeType) {
        // Just log an error, let the user save unsupported node type.
        // This will be stopped by the validation when its available in the node config.
        console.warn(`Invalid node type ${nodeType}`);
      }
      if (configuration) {
        node.configuration = { ...configuration };
      }
      if (nodeType) {
        node.nodeType = nodeType;
      }
      if (outputPorts?.length) {
        // Update the port of the source edge as well
        node.outputPorts = outputPorts;
        Object.keys(newEdges).forEach((key) => {
          if (node.id === newEdges[key]?.source?.nodeId) {
            newEdges[key].source.port = outputPorts?.[0];
          }
        });
      }
      if (inputPorts?.length) {
        node.inputPorts = inputPorts;
        // Update the port of the destination edge as well
        Object.keys(newEdges).forEach((key) => {
          if (node.id === newEdges[key]?.destination?.nodeId) {
            newEdges[key].destination.port = inputPorts?.[0];
          }
        });
      }
      if (label) {
        node.name = label;
        node.label = label;
      }
    }
  } else if (event.action === NODE_ACTION.UPDATE) {
    if (!event.originModel) {
      return;
    }
    // Updating node position;
    if (event.updateModel.x) {
      const node = findNode(newNodes, event.originModel.id);
      if (node) {
        const x = Math.floor(event.updateModel.x);
        const y = Math.floor(event.updateModel.y);
        if (node.location) {
          node.location = {
            ...node.location,
            x,
            y,
          };
        } else {
          node.x = x;
          node.y = y;
        }
      }
    }
    // Updating edge anchor of the entity graph
    if (!isUndefined(event.updateModel.targetAnchor) || !isUndefined(event.updateModel.sourceAnchor)) {
      if (event.originModel?.edgeType === AppConstants.EDGE_TYPE.USER_PREFERENCE) {
        newEdges = updateEntitygGraphAnchor(newEdges, {
          ...event.updateModel,
          id: event.originModel?.id,
          edgeType: event.originModel.edgeType,
        });
      } else {
        newEdges = updateAnchor(newEdges, event);
      }
    }

    // Updating collapsed state of group
    if (event.item?.type === 'group' && typeof event.updateModel?.collapsed === 'boolean') {
      const updatedGroup = find(newGroups, { id: event.item.id });
      if (updatedGroup) {
        updatedGroup.collapsed = event.updateModel.collapsed;
      }
    }

    nodeGroupValidationMessage = validateChangedNodeGroup(newNodes, newEdges, event);
    handleChangedNodeGroup(newNodes, event, nodeGroupValidationMessage);
  }

  return {
    nodes: newNodes,
    edges: newEdges,
    groups: newGroups,
    nodeGroupValidationMessage,
  };
}

function updateAnchor(edges, meta) {
  return produce(edges, (draftEdges) => {
    const { item, updateModel } = meta;

    if (item.isEdge) {
      const edge = find(edges, (edge) => edge.id === item.id);
      if (edge) {
        if (updateModel.target) {
          edge.destination.nodeId = updateModel.target;
          edge.destination.anchor = updateModel.targetAnchor;
        } else if (updateModel.source) {
          edge.source.nodeId = updateModel.source;
          edge.source.anchor = updateModel.sourceAnchor;
        }
      }
    }
  });
}

function updateEntitygGraphAnchor(edges, updates) {
  return produce(edges, (draftEdges) => {
    each(draftEdges, (edge) => {
      if (updates.target) {
        if (edge.destination?.nodeId === updates.target) {
          edge.destination.anchor = String(updates.targetAnchor);
        }
        if (edge.id === updates.id) {
          edge.targetAnchor = String(updates.targetAnchor);
        }
      } else if (updates.source) {
        if (edge.destination?.nodeId === updates.source) {
          edge.source.anchor = String(updates.sourceAnchor);
        }
        if (!isUndefined(edge?.id) && edge.id === updates.id) {
          edge.sourceAnchor = String(updates.sourceAnchor);
        }
      }
    });
  });
}

export function generateGraphEdgeAnchorIds(graph) {
  const { edges: newEdges, draft } = graph;
  let generatedEdges = [];
  generatedEdges = generatedEdges.concat(generateEdgeAnchorIds(newEdges));

  if (!isEmpty(draft) && draft.edges) {
    generatedEdges = generatedEdges.concat(generateEdgeAnchorIds(draft.edges));
  }

  return generatedEdges;
}

export function generateEdgeAnchorIds(edges) {
  return edges.filter((edge) => !edge.id || edge.id?.length < 12).map((edge) => ({ ...edge, id: ObjectID.generate() }));
}

export function createNode(nodes, model) {
  // Note: Hardcoded values for now until we have proper connector entities
  let { configuration, label: name, nodeType } = model;
  let inputPorts, outputPorts, id;
  if (model.entityDirection === 'sink') {
    nodeType = 'ENTITY_SINK';
    inputPorts = [];
    outputPorts = [
      {
        portType: 'INPUT',
        maxConnections: 1,
        datatype: 'object',
      },
    ];
    configuration = {
      entityDefinition: model.entityId,
    };
    name = model.entityName;
  } else if (model.entityDirection === 'source') {
    nodeType = 'ENTITY_SOURCE';
    inputPorts = [
      {
        portType: 'OUTPUT',
        maxConnections: 1,
        datatype: 'object',
      },
    ];
    outputPorts = [];
    configuration = {
      entityDefinition: model.entityId,
    };
    name = model.entityName;
  } else if (model.attributeDirection === 'sink') {
    nodeType = 'ATTRIBUTE_SINK';
    outputPorts = [];
    inputPorts = [
      {
        portType: 'INPUT',
        maxConnections: 1,
        datatype: 'string',
      },
    ];
    configuration = {
      attributeDefinition: model.attributeId,
    };
    name = model.attributeName;
  } else if (model.attributeDirection === 'source') {
    nodeType = 'ATTRIBUTE_SOURCE';
    outputPorts = [
      {
        portType: 'OUTPUT',
        maxConnections: 1,
        datatype: 'string',
      },
    ];
    inputPorts = [];
    configuration = {
      attributeDefinition: model.attributeId,
    };
    name = model.attributeName;
  } else if (META_CONFIGURATION_NODE_TYPE.includes(model.nodeType)) {
    configuration = {
      ...(model?.configuration || {}),
      ...(model?.metadata?.configuration || {}),
    };
    inputPorts = model?.metadata?.inputPorts || model?.inputPorts;
    outputPorts = model?.metadata?.outputPorts || model?.outputPorts;
    nodeType = model?.metadata?.nodeType || model?.nodeType;
    name = model?.metadata?.name || model?.name || model?.label || model?.metadata?.label;
  }

  const generateUniqueName = generateUniqueNamesCallback(nodes.map((node) => node.name));
  name = NON_UNIQUE_NODE_SHAPES.includes(model.shape) ? name : generateUniqueName(name);

  return [
    ...nodes,
    {
      name,
      id: id || model.id,
      inputPorts,
      configuration,
      nodeType,
      location: {
        x: model.x,
        y: model.y,
      },
      outputPorts,
    },
  ];
}

export function removeNode(nodes, edges, nodeId) {
  return nodes.filter((node) => node.id !== nodeId);
}

export function removeNodeEdges(edges, nodeId) {
  return edges.filter((edge) => {
    // Remove the edge with matching destination
    if (edge.destination?.nodeId === nodeId) {
      return false;
    }
    // Remove the edge with matching source
    if (edge.source?.nodeId === nodeId) {
      return false;
    }
    return true;
  });
}

export function updateEdge(edges) {
  return cloneDeep(edges);
}

// Note: This function will mutate the edges
export function createEdge(edges, model, source, target) {
  let destinationDatatype = 'object';
  let sourceDatatype = 'object';

  if (source.model?.metadata?.outputPorts?.length) {
    sourceDatatype = source.model?.metadata?.outputPorts[0]?.datatype;
  }
  if (target.model?.metadata?.inputPorts?.length) {
    destinationDatatype = target.model?.metadata?.inputPorts[0]?.datatype;
  }

  return [
    ...edges,
    {
      destination: {
        anchor: String(model.targetAnchor),
        nodeId: model.target,
        port: {
          portType: 'INPUT',
          datatype: destinationDatatype,
          maxConnections: 1,
        },
      },
      id: model.id,
      source: {
        anchor: String(model.sourceAnchor),
        nodeId: model.source,
        port: {
          maxConnections: 1,
          datatype: sourceDatatype,
          portType: 'OUTPUT',
        },
      },
    },
  ];
}

export function createGroup(groups, model) {
  return [
    ...groups,
    {
      ...model,
    },
  ];
}

export function removeEdge(edges, edgeId) {
  return edges.filter((edge) => edge.id !== edgeId);
}

/**
 * Calculate new node locations based on where the fragment was dropped on the
 * canvas. We start with the top left x,y locations from the set of nodes and
 * then move them to start at the x,y location from the dropped fragment event.
 */
export const getNewFragmentNodeLocations = (droppedLocation, nodes) => {
  const { x, y } = droppedLocation;

  const locationNodes = nodes
    .filter(
      // Core nodes can be included in a fragment but are not moved when
      // dropping a fragment. We should not include them in the position calculation
      (node) => ![AppConstants.NODE_TYPE.CORE_ENTITY, AppConstants.NODE_TYPE.CORE_ATTRIBUTE].includes(node.nodeType)
    )
    .map((node) => ({
      id: node.templateId,
      x: parseInt(node.location.x, 10),
      y: parseInt(node.location.y, 10),
    }));

  const furthestLeftX = Math.min(...locationNodes.map((node) => node.x));
  const highestY = Math.min(...locationNodes.map((node) => node.y));

  return locationNodes.reduce((prev, node) => {
    const startingXDiff = node.x - furthestLeftX;
    const newXLocation = x + startingXDiff;

    const startingYDiff = node.y - highestY;
    const newYLocation = y + startingYDiff;

    prev[node.id] = {
      x: `${Math.round(newXLocation)}`,
      y: `${Math.round(newYLocation)}`,
    };
    return prev;
  }, {});
};

export function transformAttributeNodes(attributeNodes, connectors) {
  const sinkFields = [],
    sourceFields = [];
  if (attributeNodes) {
    each(attributeNodes, (node) => {
      let nodeType;
      if (node.type === 'sink') {
        nodeType = AppConstants.NODE_TYPE.ATTRIBUTE_SINK;
      } else if (node.type === 'source') {
        nodeType = AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE;
      }
      if (!nodeType) {
        return;
      }
      const conn = find(connectors, (co) => {
        return co.id === node.connectorId;
      });
      let icon;
      if (node?.iconPath) {
        icon = node.iconPath;
      } else if (conn?.iconUri) {
        icon = conn.iconUri;
      } else if (conn?.icon) {
        icon = conn.icon;
      } else {
        console.error(`Unexpected empty icon for node ${node.name}`);
      }
      const id = ObjectID.generate();
      if (nodeType === AppConstants.NODE_TYPE.ATTRIBUTE_SINK) {
        sinkFields.push({
          configId: node.id,
          id,
          key: id,
          icon,
          connectorId: node.connectorId,
          connectorName: node.connectorName,
          title: node.label ? node.label : node.name,
          name: node.name,
          typeColor: AppConstants.FLOW_TYPE_COLOR.CONNECTOR,
          nodeType,
          noicon: true,
        });
      } else {
        sourceFields.push({
          configId: node.id,
          id,
          key: id,
          icon,
          connectorId: node.connectorId,
          connectorName: node.connectorName,
          title: node.label ? node.label : node.name,
          name: node.name,
          typeColor: AppConstants.FLOW_TYPE_COLOR.CONNECTOR,
          nodeType,
          noicon: true,
        });
      }
    });
  }
  return {
    sinkFields,
    sourceFields,
  };
}

export function getPipelineFunctions(context, data) {
  if (context === AppConstants.PIPELINE_CONTEXT.ENTITY) {
    return data.entityPipelineFunctions;
  } else {
    return data.fieldPipelineFunctions;
  }
}

export function getPipelineActions(context, data) {
  if (context === AppConstants.PIPELINE_CONTEXT.ENTITY) {
    return data.entityPipelineActions;
  } else {
    return data.fieldPipelineActions;
  }
}

export function getDefaultGraphVersion(pipelineStatus) {
  switch (pipelineStatus) {
    case AppConstants.SYNCARI_NODE_STATUS.NEW:
    case AppConstants.SYNCARI_NODE_STATUS.APPROVED:
      return pipelineStatus;
    case AppConstants.SYNCARI_NODE_STATUS.PUBLISHED:
      return AppConstants.GRAPH_STATUS.APPROVED;
    case AppConstants.SYNCARI_NODE_STATUS.PUBLISHED_WITH_DRAFT:
      return AppConstants.GRAPH_STATUS.DRAFT;
    case AppConstants.SYNCARI_NODE_STATUS.DRAFT:
      return AppConstants.GRAPH_STATUS.NEW;
    default:
      return AppConstants.GRAPH_STATUS.NEW;
  }
}

export function getPipelineDraftStatus(status?: string) {
  return [GRAPH_STATUS.DRAFT, GRAPH_STATUS.NEW].includes(status?.toUpperCase() as 'NEW' | 'DRAFT')
    ? GRAPH_STATUS.NEW
    : GRAPH_STATUS.APPROVED;
}

/**
 * Returns a valid graphStatus based on the pipeline draftStatus
 * and the draft field
 */
export function getDefaultGraphVersionFromPipeline(pipeline) {
  return isEmpty(pipeline.draft)
    ? pipeline.draftStatus || AppConstants.GRAPH_STATUS.DRAFT
    : AppConstants.GRAPH_STATUS.DRAFT;
}

export function getGraphVersionUrl(entityId, graphVersion, fieldId) {
  if (entityId) {
    if (fieldId) {
      return replaceToken(RouteConstants.FIELD_PIPELINE_GRAPH_VERSION, { entityId, graphVersion, fieldId });
    } else {
      return replaceToken(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, { entityId, graphVersion });
    }
  }
}

export function navigateToGraphVersion(options) {
  const { entityId, graphVersion, fieldId, updateSelectedNodeIdsQueryParam, nodeIds, replace = true } = options;
  const url = getGraphVersionUrl(entityId, graphVersion.toLowerCase(), fieldId);
  if (url) {
    if (updateSelectedNodeIdsQueryParam && nodeIds) {
      updateSelectedNodeIdsQueryParam(nodeIds, url);
    } else if (url !== window.location.pathname) {
      navigate(url, { replace });
    }
  }
}

export const isValidGraphVersion = (graphVersion?: string) => {
  if (!graphVersion) {
    return false;
  }

  return [GRAPH_STATUS.APPROVED, GRAPH_STATUS.APPROVED, GRAPH_STATUS.DRAFT].includes(graphVersion.toUpperCase());
};

export function hasDanglingEdge(edges) {
  return edges.some((edge) => edge.destination.anchor === 'undefined' || isUndefined(edge.destination.anchor));
}

export function areValidNodes(nodes) {
  // TODO: Simple validation for now
  return Array.isArray(nodes);
}

export function areValidEdges(edges) {
  // TODO: Simple validation for now
  return Array.isArray(edges);
}

export function isGraphEditable(version) {
  return [AppConstants.GRAPH_STATUS.NEW, AppConstants.GRAPH_STATUS.DRAFT].includes(version);
}

export const nameIsLoopSubNode = (name: string) => {
  if (!name) {
    return false;
  }
  return ['For Each', 'End Loop'].includes(name);
};

export const findOrphanedLoopSubNodes = (nodes, edges) => {
  // Identify all nodeIds with loopStart = true
  const loopStartNodeIds = nodes
    .filter((node) => node.model?.metadata?.configuration?.loopStart === true)
    .map((node) => node.id);

  // Filter nodes that have shape = 'LOOP_SIDE_FUNCTION' and are not
  // connected to loopStart nodes
  return nodes.filter((node) => {
    if (node.model.shape !== AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION) {
      return false;
    }

    const isConnectedToLoopStart = edges.some((edge) => {
      // End Loop node is the source and loop is destination
      if (node.model?.label === 'End Loop') {
        return loopStartNodeIds.includes(edge.destination.nodeId) && edge.source.nodeId === node.id;
      }

      // Loop is the source and For Each and After nodes are the destination
      return loopStartNodeIds.includes(edge.source.nodeId) && edge.destination.nodeId === node.id;
    });

    return !isConnectedToLoopStart;
  });
};

export const isConnectingLoopStartAndLoopSide = (edge, nodes) => {
  // Find the source and destination nodes
  const sourceNode = nodes.find((node) => node.id === edge.source.id);
  const destinationNode = nodes.find((node) => node.id === edge.target.id);

  // Check if one node has loopStart = true and the other has shape = 'LOOP_SIDE_FUNCTION'

  if (sourceNode?.model?.label === 'End Loop') {
    return (
      destinationNode?.model?.metadata?.configuration?.loopStart === true &&
      sourceNode?.model?.shape === AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION
    );
  }

  return (
    sourceNode?.model?.metadata?.configuration?.loopStart === true &&
    destinationNode?.model?.shape === AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION
  );
};

export const nodeIsSubnodeOfLoop = (loopId: string, edges) => (node) => {
  if (node?.model?.shape !== AppConstants.GRAPH_NODE_SHAPES.LOOP_SIDE_FUNCTION) {
    return false;
  }

  if (node?.model?.label === 'End Loop') {
    return edges.some((edge) => edge.target.id === loopId && edge.source.id === node?.id);
  }

  return edges.some((edge) => edge.source.id === loopId && edge.target.id === node?.id);
};
