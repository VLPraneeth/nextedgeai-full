import {
  addEdge,
  Background,
  BackgroundVariant,
  Controls,
  Edge,
  MarkerType,
  MiniMap,
  ReactFlow,
  useEdgesState,
  useNodesState,
} from '@xyflow/react';
import { toNumber } from 'lodash';
import { useCallback, useEffect, useMemo } from 'react';

import { Node } from 'store/pipeline/types';

import FloatingEdge from './FloatingEdge';
import ReactFlowCustomNode from './ReactFlowCustomNode';

import '@xyflow/react/dist/style.css';
import './PipelineEditorV2.scss';

const legacyToFlowNodes = (nodes: Node[]) => {
  return nodes?.map((node: any) => {
    // console.log('node', node);
    return {
      id: node.metadata.id,
      // type: 'input',
      data: node,
      type: 'customNode',
      position: {
        x: toNumber(node.metadata.location.x),
        y: toNumber(node.metadata.location.y),
      },
    };
  });
};

const legacyToFlowEdges = (edges: any[]) => {
  return edges?.map((edge) => /* console.log('edge', edge);*/ ({
    id: edge.id,
    source: edge.source,
    type: 'floatingEdge',
    target: edge.target,
    markerEnd: { type: MarkerType.ArrowClosed },
  }));
};

const generateAdditionaNodes = (initialNodes: any[]) => {
  const nodesitthis = [...initialNodes];

  const count = 100;

  for (let index = 0; index < count; index++) {
    const copyNode = initialNodes[0];
    nodesitthis.push({
      ...copyNode,
      id: copyNode.id + index,
      position: {
        x: copyNode.position.x - 100 * index,
        y: copyNode.position.y - 100 * index,
      },
    });
  }

  for (let index = 0; index < count; index++) {
    const copyNode = initialNodes[2];
    nodesitthis.push({
      ...copyNode,
      id: copyNode.id + index,
      position: {
        x: copyNode.position.x + 100 * index,
        y: copyNode.position.y + 100 * index,
      },
    });
  }

  for (let index = 0; index < count; index++) {
    const copyNode = initialNodes[1];
    nodesitthis.push({
      ...copyNode,
      id: copyNode.id + index,
      position: {
        x: copyNode.position.x - 100 * index,
        y: copyNode.position.y - 100 * index,
      },
    });
  }

  for (let index = 0; index < count; index++) {
    const copyNode = initialNodes[3];
    if (copyNode) {
      nodesitthis.push({
        ...copyNode,
        id: copyNode.id + index,
        position: {
          x: copyNode.position.x + 100 * index,
          y: copyNode.position.y + 100 * index,
        },
      });
    }
  }

  return nodesitthis;
};

export interface ExampleReactFlowProps {
  nodes: any[];
  edges: any[];
}

const nodeTypes = { customNode: ReactFlowCustomNode };
const edgeTypes = { floatingEdge: FloatingEdge };

const ExampleReactFlow = ({ nodes: rawNodes, edges: rawEdges }: ExampleReactFlowProps) => {
  const initialNodes = legacyToFlowNodes(rawNodes);

  // console.log('initialNodes', initialNodes);
  const initialEdges = legacyToFlowEdges(rawEdges);

  useEffect(() => {
    console.log(JSON.stringify(initialNodes, null, 4));
    console.log(JSON.stringify(initialEdges, null, 4));
  }, []);

  const a = useMemo(() => generateAdditionaNodes(initialNodes), []);

  const [nodes, setNodes, onNodesChange] = useNodesState(a);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(initialEdges);

  const onConnect = useCallback(
    (params) =>
      setEdges((eds) =>
        addEdge(
          {
            ...params,
            type: 'floatingEdge',
            markerEnd: { type: MarkerType.ArrowClosed },
          },
          eds
        )
      ),
    [setEdges]
  );

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      fitView
      minZoom={0.1} // Minimum zoom level
      maxZoom={4}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnect}>
      <Controls />
      <MiniMap pannable zoomable />
      <Background variant={BackgroundVariant.Dots} gap={12} size={1} />
    </ReactFlow>
  );
};

export default ExampleReactFlow;
