import { NodeProps } from '@xyflow/react';
import { EdgeType } from 'components/graph/useEdgeOptionsMenu';
import { SWITCH_CASE_BUILT_IN_CASE } from 'components/switch-case/SwitchCase.contants';
import { useEnhancedSelector } from 'hooks/redux';
import { find } from 'lodash';
import { selectAllPipelineFunctionsAsMap } from 'selectors/pipelineSelectors';
import { tCommon as tc } from 'utils/i18nUtil';
import { EDITABLE_PILL_NODES } from '../customNodes/PillNode';
import { ExtraDataFunctionActionNode, ReactFlowNodeV2 } from '../types/ReactFlow.types';
import useEnhancedReactFlow from './useEnhancedReactFlow';

const useOptionsForPredicateNode = (node: NodeProps<ReactFlowNodeV2>) => {
  const pipelineFunctions = useEnhancedSelector(selectAllPipelineFunctionsAsMap);

  const edges = useEnhancedReactFlow().getEdges();
  const nodes = useEnhancedReactFlow().getNodes();

  const fnName = (node.data.extraData as ExtraDataFunctionActionNode).functionActionApiName;

  if (node.type !== 'pillNode' || !EDITABLE_PILL_NODES.includes(fnName)) {
    return null;
  }

  // Find the source node for this predicate
  const sourceEdge = edges.find((edge) => edge.target === node.id);
  const sourceNode = nodes.find((node) => node.id === sourceEdge?.source);
  const sourceFunctionId = sourceNode?.data.fullNode.configuration?.configId;

  if (!sourceFunctionId) {
    return null;
  }

  const sourceOptionsFunction = pipelineFunctions[sourceFunctionId];
  if (!sourceOptionsFunction) {
    return null;
  }

  const configuration = find(sourceOptionsFunction.configuration, { name: 'edgeOptions' });
  if (!configuration || !('options' in configuration)) {
    return null;
  }

  const addOptions: Record<string, string> =
    configuration?.edgeType === EdgeType.case
      ? {
          [SWITCH_CASE_BUILT_IN_CASE.DEFAULT]: tc('default'),
        }
      : {};

  let options: Record<string, string | boolean> = configuration.options;

  sourceNode.data.fullNode.configuration?.case?.cases?.forEach(({ caseName }) => {
    if (caseName) {
      options[caseName] = caseName;
      if (!addOptions[SWITCH_CASE_BUILT_IN_CASE.ANY]) {
        addOptions[SWITCH_CASE_BUILT_IN_CASE.ANY] = tc('any');
      }
    }
  });

  return { ...options, ...addOptions };
};

export default useOptionsForPredicateNode;
