import { isEmpty } from 'lodash';
import { useMemo } from 'react';

import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import { useConnectorIdToMetadataMap } from 'store/connectors';
import { GraphStatus } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';

import { PipelineEditorProps } from '../../PipelineEditor.types';

const { PIPELINE_CONTEXT } = AppConstants;

const useSetupPipelineLegacy = (props: PipelineEditorProps) => {
  const initialize = () => {
    props.setDisplayedGraph((props.graphVersion?.toUpperCase() as GraphStatus) || AppConstants.GRAPH_STATUS.NEW);

    props.clearError();
    props.clearError();

    props.getPipeline();

    if (isEmpty(props.connectors)) {
      // Connectors are needed for the Synapse Entities panel and for test results
      props.getConnectors();
    }

    const pipelineId = props.isEntityPipeline ? props.entityId : (props.fieldId as string);
    if (!props.pipelineFunctions?.length) {
      props.getPipelineFunctions(pipelineId);
    }
    if (props.isEntityPipeline && props.graphVersion) {
      props.getSchemaForEntity?.({ entityId: props.entityId, graphVersion: props.graphVersion });
    }
    if (!props.pipelineActions?.length) {
      props.getPipelineActions(pipelineId);
    }
    if (props.isEntityPipeline) {
      props.getConnectorEntities(props.entityId);
    } else {
      props.getAttributeNodes(props.fieldId as string);
    }

    if (!props.entities) {
      props.getEntities();
    }

    props.setPipelineContext(props.isEntityPipeline ? PIPELINE_CONTEXT.ENTITY : PIPELINE_CONTEXT.FIELD);
    props.setPipelineId(props.entityId);

    props.getUserPreference();
  };

  useMountUnmountEffect(initialize);

  const connectorIdToMetadataMap = useConnectorIdToMetadataMap();

  const supplementalNodeData = useMemo(() => {
    return {
      pipelineFunctions: props.pipelineFunctions,
      ...(props.isEntityPipeline
        ? { connectorEntities: props.connectorEntities, entitySchema: props.entitySchema }
        : { attributeNodes: props.attributeNodes }),
      pipelineActions: props.pipelineActions,
      connectorIdToMetadataMap,
    };
  }, [
    connectorIdToMetadataMap,
    props.attributeNodes,
    props.connectorEntities,
    props.isEntityPipeline,
    props.pipelineActions,
    props.pipelineFunctions,
    props.entitySchema,
  ]);

  return supplementalNodeData;
};

export default useSetupPipelineLegacy;
