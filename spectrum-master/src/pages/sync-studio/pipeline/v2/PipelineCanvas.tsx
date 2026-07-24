import {
  Background,
  BackgroundVariant,
  Controls,
  Edge,
  EdgeChange,
  IsValidConnection,
  MiniMap,
  NodeChange,
  ProOptions,
  ReactFlow,
  useEdgesState,
  useNodesState,
  Viewport,
} from '@xyflow/react';
import { throttle } from 'lodash';
import { useCallback, useEffect, useMemo } from 'react';

import { useEnhancedDispatch } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { setCurrentGraph } from 'store/pipeline/actions';
import { updateSyncStudioPipelineViewports } from 'store/user/thunks';

import { PipelineEditorProps } from '../PipelineEditor.types';
import {
  useOnNodeClick,
  useOnNodeDoubleClick,
  useUnstackNodes,
  usePillDropdownContext,
} from './context/PillDropdownContext';
import { usePipelineEditor } from './context/PipelineEditorV2.context';
import CustomConnectionLine from './customEdges/CustomConnectionLine';
import customEdgeTypes from './customEdges/customEdgeTypes';
import customNodeTypes from './customNodes/customNodeEdgeTypes';
import useOnConnect from './hooks/useOnConnect';
import useOnDelete from './hooks/useOnDelete';
import useSelectUserPipelineViewportMatrix from './hooks/useSelectUserPipelineViewportMatrix';
import { useHandleNodeDrop, validConnectionSelector } from './PipelineEditorV2.utils';
import { flowToLegacyEdges, flowToLegacyNodes, legacyToFlowEdges, legacyToFlowNodes } from './PipelineTransformer';
import ReactFlowHandlers from './ReactFlowHandlers';

const proOptions: ProOptions = { hideAttribution: true };

export interface PipelineCanvasProps {}

const PipelineCanvas = (props: PipelineEditorProps) => {
  const { pipeline } = props;

  const basePipeline = props.displayedGraph === 'DRAFT' && pipeline.draft ? pipeline.draft : pipeline;
  const { nodes: rawNodes, edges: rawEdges } = basePipeline;

  const dispatch = useEnhancedDispatch();

  const { setHasUnsavedChanges, supplementalNodeData, isDraft } = usePipelineEditor();

  const onNodeClick = useOnNodeClick();
  const onNodeDoubleClick = useOnNodeDoubleClick();
  const unstackNodes = useUnstackNodes();

  const initialNodes = useMemo(() => {
    return legacyToFlowNodes(rawNodes, supplementalNodeData) || EMPTY_ARRAY;
  }, [rawNodes, supplementalNodeData]);

  const pipelineId = props.isEntityPipeline ? props.entityId : (props.fieldId as string);

  const viewportProps = useSelectUserPipelineViewportMatrix(pipelineId);

  const { setDropdownVisibleNodeId } = usePillDropdownContext();

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const handleViewportChange = useCallback(
    throttle((viewport: Viewport) => {
      const matrix = [viewport.zoom, 0, 0, 0, viewport.zoom, 0, viewport.x, viewport.y, 1];
      setDropdownVisibleNodeId('');

      updateSyncStudioPipelineViewports(pipelineId, matrix);
    }, 1000),
    [pipelineId]
  );

  const initialEdges = useMemo(() => legacyToFlowEdges(rawEdges) || EMPTY_ARRAY, [rawEdges]);

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(initialEdges);

  const throttledDispatch = useCallback(
    throttle((basePipeline, nodes, edges, dispatch) => {
      dispatch(
        setCurrentGraph({
          ...basePipeline,
          nodes: flowToLegacyNodes(nodes),
          edges: flowToLegacyEdges(edges),
        })
      );
    }, 500),
    []
  );

  useEffect(() => {
    throttledDispatch(basePipeline, nodes, edges, dispatch);
  }, [basePipeline, nodes, edges, dispatch, throttledDispatch]);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const handleNodesChange = useCallback(
    (nodeChanges: NodeChange<any>[]) => {
      onNodesChange(nodeChanges);
      if (nodeChanges.some((change) => change.type === 'dimensions')) {
        unstackNodes();
      }
      setHasUnsavedChanges(true);
    },
    [onNodesChange, setHasUnsavedChanges]
  );

  const handleEdgesChange = useCallback(
    (edgeChanges: EdgeChange<Edge>[]) => {
      onEdgesChange(edgeChanges);
      setHasUnsavedChanges(true);
    },
    [onEdgesChange, setHasUnsavedChanges]
  );

  useEffect(() => {
    if (!nodes.length) {
      setNodes(initialNodes);
      setEdges(initialEdges);
    }
  }, [initialNodes, initialEdges, setNodes, setEdges, nodes.length]);

  const onConnect = useOnConnect(setNodes, setEdges, supplementalNodeData);
  const onDelete = useOnDelete(setNodes);

  const handleDrop = useHandleNodeDrop(supplementalNodeData, setNodes, setEdges);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const isValidConnection: IsValidConnection = useCallback(validConnectionSelector(nodes, edges), [nodes, edges]);

  return (
    <ReactFlow
      className="synri-canvas-react-flow"
      nodes={nodes}
      edges={edges}
      onlyRenderVisibleElements
      onNodesChange={handleNodesChange}
      onEdgesChange={handleEdgesChange}
      onConnect={onConnect}
      onNodeClick={onNodeClick}
      onNodeDoubleClick={onNodeDoubleClick}
      onDelete={onDelete}
      connectionLineComponent={CustomConnectionLine as any}
      isValidConnection={isValidConnection}
      nodesDraggable={isDraft}
      nodesConnectable={isDraft}
      onDrop={handleDrop}
      onNodeDragStop={unstackNodes}
      onDragOver={(event) => {
        event.preventDefault();
      }}
      proOptions={proOptions}
      onViewportChange={handleViewportChange}
      nodeTypes={customNodeTypes}
      edgeTypes={customEdgeTypes}
      minZoom={0.1}
      maxZoom={1}
      {...viewportProps}>
      <ReactFlowHandlers setSelectedGraphNode={props.setSelectedGraphNode} />
      <Controls />
      <MiniMap pannable zoomable />
      <Background variant={BackgroundVariant.Dots} gap={12} size={1} />
    </ReactFlow>
  );
};

export default PipelineCanvas;
