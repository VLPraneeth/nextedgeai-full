import { NodeProps } from '@xyflow/react';
import { ComponentType } from 'react';

import { ReactFlowNodeTypes, ReactFlowNodeV2 } from '../types/ReactFlow.types';
import CoreNode from './CoreNode';
import FunctionActionNode from './FunctionActionNode';
import PillNode from './PillNode';
import SynapseNode from './SynapseNode';

const customNodeTypes: Record<Exclude<ReactFlowNodeTypes, 'default'>, ComponentType<NodeProps<ReactFlowNodeV2>>> = {
  coreNode: CoreNode,
  synapseNode: SynapseNode,
  functionActionNode: FunctionActionNode,
  pillNode: PillNode,
};

export default customNodeTypes;
