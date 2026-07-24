import { NodeProps } from '@xyflow/react';
import { memo } from 'react';

import { tNamespaced } from 'utils/i18nUtil';

import NodeActions from '../components/NodeActions';
import { ExtraDataFunctionActionNode, ReactFlowNodeV2 } from '../types/ReactFlow.types';
import BaseCustomNode from './BaseCustomNode';
import { NodeContentBody } from './customNodeComponents/NodeContent';

const tn = tNamespaced('PipelineV2');

const FunctionActionNode = memo((props: NodeProps<ReactFlowNodeV2>) => {
  const { data } = props;
  const node = data.fullNode;
  const { functionActionName, isFunction } = data.extraData as ExtraDataFunctionActionNode;

  const functionOrActionLabel = isFunction ? tn('function_label') : tn('action_label');

  return (
    <BaseCustomNode
      nodeProps={props}
      color={isFunction ? 'cyan' : 'violet'}
      nodeActions={<NodeActions nodeId={data.fullNode.id} edit copy clone palette trash />}
      label={functionActionName}
      content={<NodeContentBody header={functionOrActionLabel} label={node.label} />}
    />
  );
});

export default FunctionActionNode;
