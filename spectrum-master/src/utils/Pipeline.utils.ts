import { message } from 'antd';
import { isUndefined, uniq } from 'lodash';

import { Connector, ConnectorMetadata } from 'reducers/connectorReducer';
import { EMPTY_ARRAY } from 'store/constants';
import { PipelineAction } from 'store/pipeline-actions';
import { PipelineFunction } from 'store/pipeline-functions';
import { Edge, Group, Node, NodeOrGroup } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';

import { NodeTypeKeys } from './AppConstants.types';
import { tNamespaced } from './i18nUtil';

const tnGroupError = tNamespaced('PipelineUtils.GroupValidationErrors');
const tnGroupToast = tNamespaced('PipelineUtils.GroupToastMessages');

export const findNode = (nodes: Node[], nodeId: string) => {
  return nodes.find((node) => node.id === nodeId);
};

export const findGroup = (groups: Group[], groupId: string) => {
  return groups.find((group) => group.id === groupId);
};

export const findGroupNodes = (nodes: Node[], groupId: string) => {
  return nodes.filter((node) => node.groupId === groupId);
};

export interface Dictionary<T> {
  [index: string]: T;
}
export interface NodeIconData {
  epFunctionsMap: Dictionary<PipelineFunction>;
  epActionsMap: Dictionary<PipelineAction>;
  fpFunctionsMap: Dictionary<PipelineFunction>;
  fpActionsMap: Dictionary<PipelineAction>;
  attributeNodesMap: Dictionary<any>;
  connectorsMap: Dictionary<Connector>;
  connectorsMetadataMap: Dictionary<ConnectorMetadata>;
}

export const getNodeIconPath = (node: Node, context: string, nodeIconData: NodeIconData) => {
  const {
    epFunctionsMap,
    epActionsMap,
    fpFunctionsMap,
    fpActionsMap,
    attributeNodesMap,
    connectorsMap,
    connectorsMetadataMap,
  } = nodeIconData;

  let nodeIconPath: string | undefined = undefined;

  switch (node.nodeType) {
    case AppConstants.NODE_TYPE.ENTITY_SOURCE:
    case AppConstants.NODE_TYPE.ENTITY_SINK:
      if (node.configuration.connectorId) {
        const connector = connectorsMap[node.configuration.connectorId];

        if (connector) {
          nodeIconPath = connectorsMetadataMap[connector.metadataId]?.iconUri ?? undefined;
        }
      }

      break;

    case AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE:
    case AppConstants.NODE_TYPE.ATTRIBUTE_SINK:
      const attributeNode = attributeNodesMap[node.configuration.configId];

      if (attributeNode) {
        nodeIconPath = attributeNode.iconPath;
      }

      break;

    case AppConstants.NODE_TYPE.FUNCTION:
      nodeIconPath =
        context === AppConstants.PIPELINE_CONTEXT.ENTITY
          ? epFunctionsMap[node.configuration.configId]?.iconPath
          : fpFunctionsMap[node.configuration.configId]?.iconPath;
      break;

    case AppConstants.NODE_TYPE.ACTION:
      nodeIconPath =
        context === AppConstants.PIPELINE_CONTEXT.ENTITY
          ? epActionsMap[node.configuration.configId]?.iconPath
          : fpActionsMap[node.configuration.configId]?.iconPath;
      break;
  }

  return nodeIconPath;
};

const isFinalAncestorNode = (nodeType: string) => {
  const ancestorNodeTypes: string[] = [
    AppConstants.NODE_TYPE.ENTITY_SOURCE,
    AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE,
    AppConstants.NODE_TYPE.CORE_ENTITY,
    AppConstants.NODE_TYPE.CORE_ATTRIBUTE,
  ];
  return ancestorNodeTypes.includes(nodeType);
};

export const findParentsFromEdges = (nodes: Node[], edges: Edge[]) => {
  let checkedNodeIds: string[] = [];
  const findParentsForNode = (nodeId: string, recursive = false): Node[] => {
    if (!recursive) {
      checkedNodeIds = [];
    }

    // Abandon findParentsFromEdges early when circular reference found
    if (checkedNodeIds.includes(nodeId)) {
      return [];
    }

    checkedNodeIds.push(nodeId);

    const originalNode = findNode(nodes, nodeId);

    if (!originalNode) {
      return [];
    }

    if (isFinalAncestorNode(originalNode.nodeType)) {
      return [originalNode];
    }

    return edges
      .filter((edge) => edge.destination.nodeId === nodeId)
      .map((edge) => {
        const node = findNode(nodes, edge.source.nodeId);
        if (node) {
          if (isFinalAncestorNode(node.nodeType)) {
            return node;
          } else {
            return findParentsForNode(node.id, true);
          }
        }
        return false;
      })
      .flatMap((node) => node)
      .filter(Boolean) as Node[];
  };

  return findParentsForNode;
};

export const isCoreNode = (nodeType: string) => {
  const coreNodeTypes: string[] = [AppConstants.NODE_TYPE.CORE_ENTITY, AppConstants.NODE_TYPE.CORE_ATTRIBUTE];
  return coreNodeTypes.includes(nodeType);
};

export const isGroupNode = (nodeType: string) => {
  return nodeType === AppConstants.NODE_TYPE.CUSTOM_GROUP;
};

export const validateNodesToCreateGroup = (nodes: Node[], edges: Edge[] | undefined, selectedNodes: NodeOrGroup[]) => {
  const findParentsForNode = findParentsFromEdges(nodes, edges ?? EMPTY_ARRAY);
  let groupDirection: string | undefined = undefined;

  for (const groupOrNode of selectedNodes) {
    // A group cannot be added to another group
    if (isGroupNode(groupOrNode.nodeType)) {
      return tnGroupError('groups_within_groups');
    }

    const node = groupOrNode as Node;

    // A core node cannot be added to another group
    if (isCoreNode(node.nodeType)) {
      return tnGroupError('core_node');
    }

    // A node cannot belong to two or more groups
    if (node.groupId) {
      return tnGroupError('multiple_groups');
    }

    const groupType = findParentsForNode(node.id);
    const sourceNodeTypes = uniq(groupType.map((node) => node.nodeType));

    // A node must be connected to another node & cannot be connected to both sides of the pipeline
    if (sourceNodeTypes.length > 1) {
      return tnGroupError('invalid_connections');
    }

    const direction = sourceNodeTypes[0];
    if (!groupDirection) {
      groupDirection = direction;
    } else if (groupDirection !== direction) {
      if (direction?.includes('SOURCE')) {
        return tnGroupError('source_mismatch');
      } else {
        return tnGroupError('destination_mismatch');
      }
    }
  }

  return undefined;
};

// Used to determine if an event occured within one second
export const eventJustBarelyOccured = (time?: number) => {
  return time && time > Date.now() - 1000;
};

export const handleChangedNodeGroup = (nodes: Node[], event: any, validationError?: string) => {
  if (event.action === AppConstants.NODE_ACTION.REMOVE && event.item.isNode && event.item?.model?.parent) {
    return message.success(tnGroupToast('removed'), 3);
  }

  if (!event.updateModel || !('parent' in event.updateModel)) {
    // Only handle model changes that include the parent
    return;
  }

  const node = findNode(nodes, event.originModel.id);
  if (!node) {
    return;
  }

  if (isUndefined(event.updateModel.parent)) {
    delete node.groupId;
    if (!event.updateModel.skipChangeNotification) {
      return message.success(tnGroupToast('removed'), 3);
    }
  } else if (validationError) {
    message.warning(validationError, 3);
  } else {
    // Only show a success message if the node was dragged into a group and not
    // on group creation.
    if (!eventJustBarelyOccured(event.item?.model?.timeAddedToGroup)) {
      message.success(tnGroupToast('added'), 3);
    }

    node.groupId = event.updateModel.parent;
  }
};

export const validateChangedNodeGroup = (nodes: Node[], edges: Edge[], event: any): string | undefined => {
  if (!event.updateModel || !('parent' in event.updateModel)) {
    // Only handle model changes that include the parent
    return;
  }

  const node = findNode(nodes, event.originModel.id);
  if (!node) {
    return;
  }

  if (!isUndefined(event.updateModel.parent)) {
    if (isCoreNode(node.nodeType)) {
      return tnGroupError('core_node');
    }

    // Check if the group is connected to a source or core node. If it is then
    // only nodes that are connected to the same side or aren't connected at all
    // can be added to the group.
    const findParentsForNode = findParentsFromEdges(nodes, edges);

    const groupNodes = nodes.filter((node) => node.groupId === event.updateModel.parent);

    let groupSource: NodeTypeKeys | null = null;
    groupNodes.some((node) => {
      const nodeSources = findParentsForNode(node.id);
      const sourceNodeTypes = uniq(nodeSources.map((node) => node.nodeType));
      if (sourceNodeTypes.length > 0) {
        groupSource = sourceNodeTypes[0];
        return true;
      }
      return false;
    });

    if (groupSource) {
      // Search the edges connected to a node to find the source nodes or core
      // nodes from where it originates
      const nodeSources = findParentsForNode(node.id);
      const sourceNodeTypes = uniq(nodeSources.map((node) => node.nodeType));

      if (sourceNodeTypes.length === 1 && sourceNodeTypes[0] !== groupSource) {
        return tnGroupError('multiple_sides');
      }
    }
  }
};

export const getChildNodeSummaryForGroup = (nodes: Node[], groups: Group[], groupId?: string) => {
  if (!groupId) {
    return;
  }

  const group = findGroup(groups, groupId);
  if (!group) {
    return;
  }

  const groupNodes = findGroupNodes(nodes, groupId);
  return getNodeSummary(groupNodes);
};

export const getNodeSummary = (nodes: Node[]) => {
  const nodeTypeCounts = {
    sources: 0,
    sinks: 0,
    actions: 0,
    functions: 0,
  };

  const summaryItems = [];

  nodes.forEach((node) => {
    switch (node.nodeType) {
      case AppConstants.NODE_TYPE.ENTITY_SOURCE:
      case AppConstants.NODE_TYPE.ATTRIBUTE_SOURCE:
        nodeTypeCounts.sources++;
        break;

      case AppConstants.NODE_TYPE.ENTITY_SINK:
      case AppConstants.NODE_TYPE.ATTRIBUTE_SINK:
        nodeTypeCounts.sinks++;
        break;

      case AppConstants.NODE_TYPE.FUNCTION:
        nodeTypeCounts.functions++;
        break;

      case AppConstants.NODE_TYPE.ACTION:
        nodeTypeCounts.actions++;
        break;
    }
  });

  if (nodeTypeCounts.sources) {
    summaryItems.push(`${nodeTypeCounts.sources} source${nodeTypeCounts.sources > 1 ? 's' : ''}`);
  }

  if (nodeTypeCounts.sinks) {
    summaryItems.push(`${nodeTypeCounts.sinks} destination${nodeTypeCounts.sinks > 1 ? 's' : ''}`);
  }

  if (nodeTypeCounts.functions) {
    summaryItems.push(`${nodeTypeCounts.functions} function${nodeTypeCounts.functions > 1 ? 's' : ''}`);
  }

  if (nodeTypeCounts.actions) {
    summaryItems.push(`${nodeTypeCounts.actions} action${nodeTypeCounts.actions > 1 ? 's' : ''}`);
  }

  return summaryItems.join(', ');
};

// How close nodes can be before being considered "stacked"
export const NODE_STACKING_BOUNDARY = 25;

export interface NodeLocation {
  id: string;
  x: number;
  y: number;
}

const nodeIsStacked = (node1: NodeLocation, node2: NodeLocation) => {
  if (node1.id === node2.id) {
    return false;
  }
  const x1 = node1.x;
  const x2 = node2.x;

  if (x1 >= x2 + NODE_STACKING_BOUNDARY) {
    return false;
  }
  if (x1 <= x2 - NODE_STACKING_BOUNDARY) {
    return false;
  }

  const y1 = node1.y;
  const y2 = node2.y;

  if (y1 >= y2 + NODE_STACKING_BOUNDARY) {
    return false;
  }
  if (y1 <= y2 - NODE_STACKING_BOUNDARY) {
    return false;
  }

  return true;
};

export const getUnstackedNodeCoordinates = (nodes: Node[]) => {
  const nodeLocations: NodeLocation[] = nodes
    .filter((node) => !isUndefined(node.location.x))
    .map((node) => ({
      id: node.id,
      x: parseInt(node.location.x, 10),
      y: parseInt(node.location.y, 10),
    }));

  if (!nodeLocations.length) {
    return EMPTY_ARRAY;
  }

  return nodeLocations
    .map((nodeLocation) => {
      let stackedNode;
      const originalLocationX = nodeLocation.x;

      do {
        stackedNode = nodeLocations.find((node2) => nodeIsStacked(nodeLocation, node2));

        if (stackedNode) {
          // The updated node will be used on subsequent iterations so when two
          // nodes are stacked we only mark one as stacked.
          // We put the stacked node 25px offset from the node it's stacked with
          // so we get a consistent output.
          nodeLocation.x = stackedNode.x + NODE_STACKING_BOUNDARY;
          nodeLocation.y = stackedNode.y + NODE_STACKING_BOUNDARY;
        }
      } while (stackedNode);

      if (originalLocationX !== nodeLocation.x) {
        return nodeLocation;
      }
      return false;
    })
    .filter(Boolean) as NodeLocation[];
};
