import { addEdge, Edge, MarkerType, OnConnect, useReactFlow } from '@xyflow/react';
import ObjectID from 'bson-objectid';
import { find, isEmpty, some } from 'lodash';
import { useCallback } from 'react';

import { SWITCH_CASE_BUILT_IN_CASE } from 'components/switch-case/SwitchCase.contants';
import { PipelineFunction } from 'store/pipeline-functions';
import AppConstants from 'utils/AppConstants';

import { getExtraDataForNode } from '../PipelineTransformer';
import { ExtraDataFunctionActionNode, ReactFlowNodeV2 } from '../types/ReactFlow.types';

const NODE_POSITION_OFFSET = { X: 75, Y: 40 };

const useOnConnect = (
  setNodes: React.Dispatch<React.SetStateAction<ReactFlowNodeV2[]>>,
  setEdges: React.Dispatch<React.SetStateAction<Edge[]>>,
  supplementalNodeData: Record<string, any>
) => {
  const nodes = useReactFlow().getNodes();
  const edges = useReactFlow().getEdges();

  const onConnect: OnConnect = useCallback(
    (params) => {
      const subNodes: ReactFlowNodeV2[] = [];
      const subNodeEdges: Edge[] = [];

      const getPredicateValue = (baseNodeSource: ReactFlowNodeV2, predicateType: 'decision' | 'switch') => {
        const isSwitch = predicateType === 'switch';

        // Get the values of other predicate nodes attached to the same source
        // so we can set the value to an option that isn't already selected if
        // possible.
        const otherPredicateNodeIds = edges
          // edge.id !== event.item.id &&
          .filter((edge) => edge.source === baseNodeSource.id)
          .map((edge) => edge.target);

        const otherPredicateNodeValues = nodes
          .filter((node: any) => otherPredicateNodeIds.includes(node.id))
          .map((node: any) => node.data.fullNode.configuration.value);

        let value: string | boolean = '';
        if (isSwitch) {
          const cases = baseNodeSource.data.fullNode.configuration?.case?.cases;
          value = cases?.[0]?.caseName || SWITCH_CASE_BUILT_IN_CASE.DEFAULT;

          const caseNames = cases?.map((caseValue: any) => {
            return caseValue.caseName;
          });
          // If the defaultValue is already on another predicate node then set,
          // then look for an unused option and set the value to it.
          if (otherPredicateNodeValues.includes(value)) {
            some(caseNames, (optionValue) => {
              if (!otherPredicateNodeValues.includes(optionValue)) {
                value = optionValue;
                return true;
              }
            });
          }
        } else {
          // Use true if there are no other predicate nodes or if there's
          // already at least on false value.
          value = isEmpty(otherPredicateNodeValues) || some(otherPredicateNodeValues, (value) => value === false);
        }

        return value;
      };

      const addPredicateNode = (
        functionName: string,
        predicateFunctionName: string,
        predicateFunctionLabel: string,
        predicateType: 'decision' | 'switch'
      ) => {
        const sourceNodes = nodes.filter(
          (node) => (node.data.extraData as ExtraDataFunctionActionNode).functionActionApiName === functionName
        );

        const baseNodeSource = sourceNodes.find(({ id }) => id === params.source) as ReactFlowNodeV2;

        if (baseNodeSource) {
          const predicateNodeId = ObjectID.generate();

          const targetNode = nodes.find(({ id }) => id === params.target) as ReactFlowNodeV2;

          // Set the new coordinates to be halfway between the two nodes

          const { x: sourceX, y: sourceY } = baseNodeSource.position;
          const { x: targetX, y: targetY } = targetNode.position;
          const newX = (targetX - sourceX) / 2 + sourceX + NODE_POSITION_OFFSET.X;
          const newY = (targetY - sourceY) / 2 + sourceY + NODE_POSITION_OFFSET.Y;

          const predicatePosition = { x: newX, y: newY };

          const predicateFunction = find(supplementalNodeData.pipelineFunctions, {
            name: predicateFunctionName,
          }) as PipelineFunction;

          const fullPredicateNode: ReactFlowNodeV2['data']['fullNode'] = {
            id: predicateNodeId,
            configuration: {
              configId: predicateFunction.id,
              definition: predicateFunction.id,
              value: getPredicateValue(baseNodeSource, predicateType),
            },
            name: `${baseNodeSource.data.fullNode.name} (${predicateFunctionLabel})`,
            nodeType: AppConstants.NODE_TYPE.FUNCTION,
            location: predicatePosition,
            label: `${baseNodeSource.data.fullNode.name} (${predicateFunctionLabel})`,
            inputPorts: [
              {
                datatype: 'object',
                maxConnections: 1,
                portType: 'INPUT',
              },
            ],
            outputPorts: [
              {
                datatype: 'object',
                maxConnections: 1,
                portType: 'OUTPUT',
              },
            ],
          };

          const predicateNode: ReactFlowNodeV2 = {
            id: predicateNodeId,
            type: 'pillNode',
            data: {
              extraData: getExtraDataForNode(
                { ...fullPredicateNode, configuration: { configId: fullPredicateNode.configuration.configId } },
                supplementalNodeData
              ),
              fullNode: fullPredicateNode,
            },
            position: predicatePosition,
          };

          subNodes.push(predicateNode);

          const predicateSourceEdge: Edge = {
            id: ObjectID.generate(),
            type: 'customEdge',
            source: baseNodeSource.id,
            target: predicateNode.id,
            markerEnd: { type: MarkerType.ArrowClosed },
          };

          subNodeEdges.push(predicateSourceEdge);

          const predicateTargetEdge: Edge = {
            id: ObjectID.generate(),
            type: 'customEdge',
            source: predicateNode.id,
            target: targetNode?.id as string,
            markerEnd: { type: MarkerType.ArrowClosed },
          };

          subNodeEdges.push(predicateTargetEdge);
        }
      };

      addPredicateNode(
        AppConstants.DECISION_FUNCTION_NAME,
        AppConstants.PREDICATE_FUNCTION_NAME,
        'Decision',
        'decision'
      );
      addPredicateNode(AppConstants.SWITCH_FUNCTION_NAME, AppConstants.CASE_BRANCH_FUNCTION_NAME, 'Case', 'switch');

      if (!subNodes.length) {
        setEdges((eds) =>
          addEdge(
            {
              ...params,
              type: 'customEdge',
            },
            eds
          )
        );
      } else {
        setNodes((currentNodes) => [...currentNodes, ...subNodes]);
        setEdges((currentEdges) => [...currentEdges, ...subNodeEdges]);
      }
    },
    [edges, nodes, setEdges, setNodes, supplementalNodeData]
  );

  return onConnect;
};

export default useOnConnect;
