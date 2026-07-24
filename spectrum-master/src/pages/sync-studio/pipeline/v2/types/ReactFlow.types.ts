import { Node } from '@xyflow/react';

import { NodeType } from 'store/pipeline/types';

import { PipelineNodeV2 } from './BackendPipeline.types';
import { FieldsCountSummary } from './PipelineV2.types';

export type ReactFlowNodeTypes = 'coreNode' | 'synapseNode' | 'functionActionNode' | 'pillNode' | 'default';

export type ReactFlowNodeV2 = Node<ReactFlowNodeData, ReactFlowNodeTypes>;

export type BaseExtraData = {
  nodeType: NodeType;
  icon: string;
};

export type ExtraDataCoreEntityNode = BaseExtraData & {
  nodeType: 'CORE_ENTITY';
  fieldsSummary: FieldsCountSummary;
};

export type ExtraDataCoreAttributeNode = BaseExtraData & {
  nodeType: 'CORE_ATTRIBUTE';
  dataType: string;
  isMultivalued: boolean;
};

export type ExtraDataFunctionActionNode = BaseExtraData & {
  nodeType: 'FUNCTION' | 'ACTION';
  functionActionName: string;
  functionActionApiName: string;
  isFunction: boolean;
  isAction: boolean;
};

export type ExtraDataEntitySourceNode = BaseExtraData & {
  nodeType: 'ENTITY_SOURCE';
  sourceName: string;
};

// Union of all possible types
export type ReactFlowNodeExtraData =
  | ExtraDataFunctionActionNode
  | ExtraDataEntitySourceNode
  | BaseExtraData
  | ExtraDataCoreAttributeNode;

export type ReactFlowNodeData = {
  extraData: ReactFlowNodeExtraData;
  fullNode: PipelineNodeV2;
};
