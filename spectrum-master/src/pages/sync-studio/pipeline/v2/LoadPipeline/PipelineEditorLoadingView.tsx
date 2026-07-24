import { Spin } from 'antd';
import { isEmpty, some } from 'lodash';
import { ReactNode, useCallback, useMemo, useState } from 'react';
import { connect } from 'react-redux';

import CenterLayout from 'components/layout/CenterLayout';
import AppConstants from 'utils/AppConstants';
import { isValidEntity } from 'utils/EntityUtil';
import { tCommon } from 'utils/i18nUtil';

import { PipelineEditorProps } from '../../PipelineEditor.types';
import { ConnectionProvider } from '../context/ConnectionContext';
import { PillDropdownProvider } from '../context/PillDropdownContext';
import { PipelineEditorContext } from '../context/PipelineEditorV2.context';
import useEnhancedReactFlow from '../hooks/useEnhancedReactFlow';
import { useSaveablePipeline } from '../hooks/useSaveablePipeline';
import { useSelectedGraphNode } from '../hooks/useSelectedGraphNode';
import { mapDispatchToPropsPipeline, mapStateToPropsPipeline } from '../PipelineEditorv2.connector';
import { PipelineNodeV2 } from '../types/BackendPipeline.types';
import { useHandlePipelineFinishedLoading } from './LoadPipeline.hooks';
import useSetupPipelineLegacy from './LoadPipelineLegacy.hooks';
import NoPipelineFound from './NoPipelineFound';

export interface PipelineEditorLoadingViewProps extends PipelineEditorProps {
  children: ReactNode;
}

const PipelineEditorLoadingView = (props: PipelineEditorLoadingViewProps) => {
  const { pipeline } = props;

  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

  const supplementalNodeData = useSetupPipelineLegacy(props);

  useHandlePipelineFinishedLoading(props);

  const reactFlow = useEnhancedReactFlow();

  const getSaveablePipeline = useSaveablePipeline();

  const savePipeline = useCallback(async () => {
    const { pipeline: storedPipeline, isEntityPipeline, updatePipeline, fieldId, entityId } = props;

    const saveablePipeline = getSaveablePipeline({ storedPipeline, reactFlow });

    if (isEntityPipeline) {
      await updatePipeline(entityId, saveablePipeline, { refreshPipelineOnUpdate: false });
      setHasUnsavedChanges(false);
    } else {
      await updatePipeline(fieldId as string, saveablePipeline, {
        refreshPipelineOnUpdate: false,
        entityId,
      });
      setHasUnsavedChanges(false);
    }
  }, [props, reactFlow, getSaveablePipeline]);

  const { selectedNode, setNodeIds } = useSelectedGraphNode();

  const saveNodeConfiguration = useCallback(
    (update: Partial<PipelineNodeV2>) => {
      const nodes = reactFlow.getNodes();

      reactFlow.setNodes(
        nodes.map((node) => {
          if (node.id === update.id) {
            return {
              ...node,
              data: {
                ...node.data,
                fullNode: { ...node.data.fullNode, ...update },
              },
            };
          }

          return node;
        })
      );

      // Timeout waits for changes to be stored in the React Flow internal state
      setTimeout(() => {
        savePipeline();
      }, 200);
    },
    [reactFlow, savePipeline]
  );

  let children = props.children;

  // Don't return the nodes until we have all of the supplemental data loaded
  const loadingExtraData = some(supplementalNodeData, (data) => isEmpty(data));

  if (props.pipelineFetching || isEmpty(pipeline) || !props.displayedGraph || loadingExtraData) {
    children = (
      <CenterLayout>
        <Spin tip={tCommon('loading')} />
      </CenterLayout>
    );

    if (props.pipelineExists === false && isValidEntity(props.entities, props.entityId)) {
      children = <NoPipelineFound {...props} />;
    }
  }

  const contextValue = useMemo(() => {
    return {
      ...props,
      hasUnsavedChanges,
      setHasUnsavedChanges,
      selectedGraphNode: selectedNode,
      setSelectedNodeIds: setNodeIds,
      saveNodeConfiguration,
      savePipeline,
      supplementalNodeData,
      isDraft: [AppConstants.GRAPH_STATUS.NEW, AppConstants.GRAPH_STATUS.DRAFT].includes(
        props.graphVersion?.toUpperCase() as any
      ),
    };
  }, [hasUnsavedChanges, props, saveNodeConfiguration, savePipeline, selectedNode, setNodeIds, supplementalNodeData]);

  return (
    <PipelineEditorContext.Provider value={contextValue}>
      <PillDropdownProvider>
        <ConnectionProvider>{children}</ConnectionProvider>
      </PillDropdownProvider>
    </PipelineEditorContext.Provider>
  );
};

export default connect(mapStateToPropsPipeline, mapDispatchToPropsPipeline)(PipelineEditorLoadingView);
