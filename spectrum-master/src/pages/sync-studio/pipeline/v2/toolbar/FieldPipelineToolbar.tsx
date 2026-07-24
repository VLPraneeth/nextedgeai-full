import { getNavigateParams, navigateTo } from 'utils/AppUtil';
import { getEntityName } from 'utils/EntityUtil';
import { navigateToGraphVersion } from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/StringUtil';

import { PipelineToolbarProps } from '../../PipelineEditor.types';
import PipelineToolbarV2, { GraphToolbarPropsV2 } from './PipelineToolbarV2';
import useToolbarProps from './useToolbarProps';

const FieldPipelineToolbar = (props: PipelineToolbarProps) => {
  const navigateToEntityPipeline = () => {
    const url = replaceToken(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, {
      entityId: props.entityId,
      graphVersion: props.graphVersion,
    });
    navigateTo(url, getNavigateParams({ ...props }));
  };

  const fpToolbarProps: Partial<GraphToolbarPropsV2> = {
    fieldId: props.fieldId,
    onChangeGraph: (evt) => {
      const newVersion = evt.key;
      navigateToGraphVersion({
        graphVersion: newVersion,
        entityId: props.entityId,
        fieldId: props.fieldId,
        replace: false,
      });
    },
    goToName: `${getEntityName(props.entityId, props.entities)}`,
    navigateUp: navigateToEntityPipeline,
    // readyToggleValue: state.ready,
    // onReadyToggleChange,
  };

  const generalToolbarProps = useToolbarProps(props);

  return <PipelineToolbarV2 {...generalToolbarProps} {...fpToolbarProps} />;
};

export default FieldPipelineToolbar;
