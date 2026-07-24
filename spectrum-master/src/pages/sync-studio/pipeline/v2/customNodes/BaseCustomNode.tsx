import { Handle, NodeProps, Position } from '@xyflow/react';
import cx from 'classnames';
import { memo, ReactNode, useCallback, useMemo, useState } from 'react';

import { setSelectedGraphNode, showNodeConfigModal } from 'actions/entityPipelineActions';
import { ReactComponent as ExclamationIcon } from 'assets/icons/exclamation.svg';
import FadeInOut from 'components/FadeInOut';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { selectEntityPipeline, selectFieldPipeline, selectPipelineContext } from 'selectors/pipelineSelectors';
import AppConstants from 'utils/AppConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useUpdateSelectedNodeIdsQueryParam } from '../../PipelineEditor.hooks';
import { useConnectionContext } from '../context/ConnectionContext';
import { PipelineNodeColors } from '../types/PipelineV2.types';
import { ReactFlowNodeV2 } from '../types/ReactFlow.types';
import './BaseCustomNode.scss';
import { NodeContentContainer } from './customNodeComponents/NodeContent';
import NodeTitleBar from './customNodeComponents/NodeTitleBar';

export interface BaseCustomNodeProps {
  nodeProps: NodeProps<ReactFlowNodeV2>;
  content: ReactNode;
  color: PipelineNodeColors | 'solid-blue';
  nodeActions: ReactNode;
  label?: string;
}

const BaseCustomNode = memo(({ content, nodeProps, label, nodeActions, color }: BaseCustomNodeProps) => {
  const { data, selected, dragging } = nodeProps;

  const graphVersion = useEnhancedSelector((state) => state.pipeline.displayedGraph);
  const pipelineContext = useEnhancedSelector(selectPipelineContext);
  const entityPipeline = useEnhancedSelector(selectEntityPipeline);
  const fieldPipeline = useEnhancedSelector(selectFieldPipeline);

  const validationState = useEnhancedSelector((state) => {
    const { warnings, errors } = state.validation;
    const nodeErrors = errors?.filter((error: any) => error.nodeId === nodeProps.id) ?? [];
    const nodeWarnings = warnings?.filter((warning) => warning.nodeId === nodeProps.id) ?? [];
    return {
      errors: nodeErrors,
      warnings: nodeWarnings,
    };
  });

  const isDraft = useMemo(
    () =>
      Boolean(
        graphVersion &&
          ([AppConstants.GRAPH_STATUS.NEW, AppConstants.GRAPH_STATUS.DRAFT] as const).includes(
            graphVersion.toUpperCase() as 'NEW' | 'DRAFT'
          )
      ),
    [graphVersion]
  );
  const { connectionSource, connectionTarget } = useConnectionContext();

  const isSelected = useMemo(() => selected || (connectionSource && connectionTarget === nodeProps.id), [
    selected,
    connectionSource,
    connectionTarget,
    nodeProps.id,
  ]);

  const showExtras = useMemo(() => !connectionSource && !dragging, [connectionSource, dragging]);

  const dispatch = useEnhancedDispatch();

  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

  const handleValidationCountClick = useCallback(
    (e) => {
      e.stopPropagation();

      const url = makeUrl(
        pipelineContext === 'field'
          ? RouteConstants.FIELD_PIPELINE_VALIDATION
          : RouteConstants.ENTITY_PIPELINE_VALIDATION,
        {
          entityId: entityPipeline?.targetId,
          graphVersion: graphVersion?.toLowerCase(),
          fieldId: fieldPipeline?.targetId,
        }
      );

      updateSelectedNodeIdsQueryParam([nodeProps.id], url);

      dispatch(setSelectedGraphNode(data.fullNode));
    },
    [
      dispatch,
      data.fullNode,
      entityPipeline,
      fieldPipeline,
      graphVersion,
      nodeProps.id,
      pipelineContext,
      updateSelectedNodeIdsQueryParam,
    ]
  );

  const [isHovered, setIsHovered] = useState(false);

  const validationIcon = useMemo(() => {
    if (!validationState) {
      return null;
    }
    return (
      <div className="validation__issues" onClick={handleValidationCountClick}>
        {validationState.errors?.length > 0 && (
          <div className="validation__error">
            <ExclamationIcon className="validation__error-icon" />
            <div className="validation__error-count">{validationState.errors.length}</div>
          </div>
        )}
        {validationState.warnings?.length > 0 && (
          <div className="validation__warning">
            <ExclamationIcon className="validation__warning-icon" />
            <div className="validation__warning-count">{validationState.errors.length}</div>
          </div>
        )}
      </div>
    );
  }, [validationState, handleValidationCountClick]);

  return (
    <>
      <div className="bottom-handle-container" style={{ opacity: showExtras && isDraft ? undefined : 0 }}>
        <Handle type="source" position={Position.Bottom} className="bottom-handle" />
      </div>
      <div
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
        onDoubleClick={() => {
          dispatch(showNodeConfigModal(true));
        }}
        className={cx('pipeline-node-wrapper', isSelected && 'selected')}>
        <div className="pipeline-node-wrapper__top-bar">
          <div className="pipeline-node-wrapper__top-bar--left-content">{validationIcon}</div>
          <img className="pipeline-node__header-icon" src={data.extraData.icon} alt="node-icon" />
          <div className="pipeline-node-wrapper__top-bar--right-content">
            <FadeInOut visible={(isHovered || selected) && showExtras && isDraft}>{nodeActions}</FadeInOut>
          </div>
        </div>
        <div className="pipeline-node">
          <NodeTitleBar color={color} label={label} />
          <NodeContentContainer>{content}</NodeContentContainer>
        </div>
      </div>
      <Handle type="target" position={Position.Bottom} className="custom-target-handle" isConnectableStart={false} />
    </>
  );
});

export default BaseCustomNode;
