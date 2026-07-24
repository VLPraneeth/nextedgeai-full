import useEffectOnValueChange from 'hooks/useEffectOnValueChange';
import usePreviousValue from 'hooks/usePreviousValue';
import { useSelectedNodes, useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { PipelineEditorProps } from 'pages/sync-studio/pipeline/PipelineEditor.types';
import { navigateToGraphVersion } from 'utils/PipelineUtil';

import { getCorrectDisplayGraph } from './LoadPipeline.utils';

export const useHandlePipelineFinishedLoading = (props: PipelineEditorProps) => {
  const wasFetching = usePreviousValue(props.pipelineFetching);
  const { selectedNodeIds } = useSelectedNodes();
  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

  const { pipeline } = props;

  useEffectOnValueChange(() => {
    if (wasFetching === true && props.pipelineFetching === false) {
      const correctGraphVersion = getCorrectDisplayGraph(props.pipeline, props.displayedGraph, pipeline.draftStatus);

      if (correctGraphVersion) {
        navigateToGraphVersion({
          ...(props.isFieldPipeline && { fieldId: props.fieldId }),
          entityId: props.entityId,
          graphVersion: correctGraphVersion,
          updateSelectedNodeIdsQueryParam,
          replace: true,
          nodeIds: selectedNodeIds,
        });
      }
    }
  }, [props.pipelineFetching]);
};
