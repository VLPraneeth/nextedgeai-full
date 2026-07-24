import { SyncariThunkDispatch } from 'hooks/redux';
import {
  mapDispatchToPropsEntityPipeline,
  mapStateToPropsEntityPipeline,
} from 'pages/sync-studio/entity-pipeline/EntityPipelineEditorProps';
import {
  mapDispatchToPropsFieldPipeline,
  mapStateToPropsFieldPipeline,
} from 'pages/sync-studio/field-pipeline/FieldPipelineEditorProps';
import { RootState } from 'reducers/index';
import { getPipelineDraftStatus } from 'utils/PipelineUtil';

import { PipelineEditorProps } from '../PipelineEditor.types';

const getPipelineType = (props: PipelineEditorProps) => {
  return {
    isEntityPipeline: !props.fieldId,
    isFieldPipeline: !!props.fieldId,
  };
};

export const mapStateToPropsPipeline = (state: RootState, props: PipelineEditorProps): PipelineEditorProps => {
  const pipelineTypes = getPipelineType(props);

  const enhancedProps = pipelineTypes.isEntityPipeline
    ? mapStateToPropsEntityPipeline(state, props)
    : mapStateToPropsFieldPipeline(state, props);

  return {
    ...enhancedProps,
    ...pipelineTypes,
    ...props,
  };
};

export const mapDispatchToPropsPipeline = (dispatch: SyncariThunkDispatch, props: PipelineEditorProps) => {
  const pipelineTypes = getPipelineType(props);

  const enhancedProps = pipelineTypes.isEntityPipeline
    ? mapDispatchToPropsEntityPipeline(dispatch)
    : mapDispatchToPropsFieldPipeline(dispatch);

  return {
    ...enhancedProps,
    ...pipelineTypes,
    getPipeline: () => {
      const graphVersion = getPipelineDraftStatus(props.graphVersion?.toUpperCase());

      if (pipelineTypes.isEntityPipeline) {
        return enhancedProps.getPipeline(props.entityId, graphVersion);
      } else {
        // Fetch the EP so we have the settings in the state.entityPipeline.entityPipeline in the store
        enhancedProps.getEntityPipeline(props.entityId, graphVersion);
        return enhancedProps.getPipeline(props.entityId, props.fieldId as any, graphVersion);
      }
    },
  };
};
