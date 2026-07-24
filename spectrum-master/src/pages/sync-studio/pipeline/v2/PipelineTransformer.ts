import { Edge, MarkerType } from '@xyflow/react';
import { find, isFinite, keyBy, toNumber } from 'lodash';

import { AttributeNode } from 'store/field-pipeline/types';
import { NodeType } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { getNodeIcon } from 'utils/PipelineUtil';
import { DeepPartial } from 'utils/TypeUtils';

import { EntitySchemaField, PipelineEdgeV2, PipelineNodeV2 } from './types/BackendPipeline.types';
import { FieldsCountSummary } from './types/PipelineV2.types';
import {
  ExtraDataCoreAttributeNode,
  ExtraDataCoreEntityNode,
  ExtraDataFunctionActionNode,
  ReactFlowNodeExtraData,
  ReactFlowNodeTypes,
  ReactFlowNodeV2,
} from './types/ReactFlow.types';

export const nodeTypeMap: Record<NodeType, ReactFlowNodeTypes> = {
  CUSTOM_GROUP: 'default',
  ENTITY_SINK: 'synapseNode',
  ENTITY_SOURCE: 'synapseNode',
  CORE_ENTITY: 'coreNode',
  CORE_ATTRIBUTE: 'coreNode',
  ATTRIBUTE_SOURCE: 'synapseNode',
  ATTRIBUTE_SINK: 'synapseNode',
  FUNCTION: 'functionActionNode',
  ACTION: 'functionActionNode',
  CONNECTOR_ENTITY: 'synapseNode',
  CONNECTOR_ATTRIBUTE: 'synapseNode',
};

const pillNodes: string[] = [
  AppConstants.FOR_EACH_FUNCTION_NAME,
  AppConstants.END_LOOP_FUNCTION_NAME,
  AppConstants.PREDICATE_FUNCTION_NAME,
  AppConstants.CASE_BRANCH_FUNCTION_NAME,
];

const getNodeType = (node: PipelineNodeV2, extraData: ReactFlowNodeExtraData): ReactFlowNodeTypes => {
  if ('functionActionApiName' in extraData && pillNodes.includes(extraData.functionActionApiName)) {
    return 'pillNode';
  }

  if (nodeTypeMap[node.nodeType]) {
    return nodeTypeMap[node.nodeType];
  }

  return 'default';
};

const getFieldsSummary = (fields: EntitySchemaField[]) => {
  return fields.reduce<FieldsCountSummary>(
    (sum, field) => {
      if (field.isMapped) {
        sum.mapped++;
      }
      if (field.hasChanges) {
        sum.draft++;
      }
      if (field.ready) {
        sum.ready++;
      }
      return sum;
    },
    {
      fieldsCount: fields.length,
      mapped: 0,
      draft: 0,
      ready: 0,
    }
  );
};

export const getExtraDataForNode = (node: PipelineNodeV2, data: any): ReactFlowNodeExtraData => {
  if (node.nodeType === 'ACTION' || node.nodeType === 'FUNCTION') {
    const isFunction = node.nodeType === 'FUNCTION';

    const items = isFunction ? data.pipelineFunctions : data.pipelineActions;
    const functionAction = find(items, { id: node.configuration.configId });
    const functionActionName = functionAction?.displayName;
    const functionActionApiName = functionAction?.name;

    const extraData: ExtraDataFunctionActionNode = {
      nodeType: node.nodeType,
      icon: getNodeIcon(node, data),
      functionActionName,
      functionActionApiName,
      isFunction,
      isAction: !isFunction,
    };

    return extraData;
  }
  if (node.nodeType === 'CORE_ENTITY') {
    const { fields } = data.entitySchema;

    const extraData: ExtraDataCoreEntityNode = {
      nodeType: node.nodeType,
      icon: '/assets/icons/syncari-square.svg',
      fieldsSummary: getFieldsSummary(fields),
    };

    return extraData;
  }

  if (node.nodeType === AppConstants.SCOPE.CORE_ATTRIBUTE) {
    const coreNode = data.attributeNodes.find((n: AttributeNode) => n.isCoreNode);

    const config = keyBy(coreNode.configuration, 'name');

    const extraData: ExtraDataCoreAttributeNode = {
      nodeType: node.nodeType,
      icon: '/assets/icons/syncari-square.svg',
      dataType: config.dataType?.defaultValue,
      isMultivalued: config.multiValue?.defaultValue,
    };

    return extraData;
  }

  return {
    nodeType: node.nodeType,
    icon: getNodeIcon(node, data),
  };
};

export const legacyToFlowNodes = (nodes: PipelineNodeV2[], data: any): ReactFlowNodeV2[] => {
  return nodes?.map((node: PipelineNodeV2) => {
    const { location, ...rest } = node;

    // Sometimes the core node is missing a location. The G6 editor seemed to
    // handle that fine but it breaks react flow. We're adding a default x/y
    // here to accomodate.
    const x = isFinite(toNumber(location.x)) ? toNumber(location.x) : 600;
    const y = isFinite(toNumber(location.y)) ? toNumber(location.y) : 400;

    const extraData = getExtraDataForNode(node, data);

    return {
      id: node.id,
      type: getNodeType(node, extraData),
      data: {
        extraData,
        fullNode: {
          ...rest,
          location,
          id: node.id,
        },
      },
      position: { x, y },
    };
  });
};

export const flowToLegacyNode = (node: ReactFlowNodeV2): PipelineNodeV2 => ({
  ...node.data.fullNode,
  location: { x: node.position.x, y: node.position.y }, // Location from position
});

export const flowToLegacyNodes = (flowNodes: ReactFlowNodeV2[]) => {
  return flowNodes.map(flowToLegacyNode);
};

export const legacyToFlowEdges = (edges: any[]): Edge[] => {
  return edges?.map((edge) => ({
    id: edge.id,
    type: 'customEdge',
    source: edge.source.nodeId,
    target: edge.destination.nodeId,
    markerEnd: { type: MarkerType.ArrowClosed },

    // These fields are added for toggling between canvas v1 and v2. We don't
    // bother with defining anchor handles in the v2 canvas.
    sourceHandleLegacy: edge.source.anchor,
    targetHandleLegacy: edge.destination.anchor,
  }));
};

// TODO: Preserve the anchor when switching between legacy and new edges
export const flowToLegacyEdges = (edges: Edge[]): DeepPartial<PipelineEdgeV2>[] => {
  return edges?.map((edge) => ({
    id: edge.id,
    source: {
      anchor: (edge as any).sourceHandleLegacy || '1',
      nodeId: edge.source,
      port: {
        datatype: 'object',
        maxConnections: 2147483647,
        portType: 'OUTPUT',
      },
    },
    destination: {
      anchor: (edge as any).targetHandleLegacy || '1',
      nodeId: edge.target,
      port: { portType: 'INPUT', datatype: 'object', maxConnections: 2147483647 },
      datatype: 'object',
      portType: 'INPUT',
    },
    originalId: edge.id,
  }));
};
