import { toLower } from 'lodash';
import { useMemo } from 'react';

import { GRAPH_MODE } from 'components/GraphPage';
import AppConstants from 'utils/AppConstants';

import { PipelineEditorProps } from '../PipelineEditor.types';

const { GRAPH_STATUS } = AppConstants;

export const useGraphMode = (props: PipelineEditorProps): GRAPH_MODE => {
  return useMemo(() => {
    if (toLower(props.graphVersion) === toLower(AppConstants.GRAPH_STATUS.APPROVED)) {
      // Published Pipeline
      return GRAPH_MODE.READ_SELECT_NODE_ONLY;
    } else {
      // Draft Pipeline
      if (props.testResultVisible) {
        return GRAPH_MODE.READ_SELECT_NODE_ONLY;
      }

      if (props.nodeCheckMode) {
        return GRAPH_MODE.READ_CHECK_NODE_ONLY;
      }

      if (props.dragSelectMode) {
        return GRAPH_MODE.DRAG_SELECT;
      }

      return GRAPH_MODE.DEFAULT;
    }
  }, [props.dragSelectMode, props.graphVersion, props.nodeCheckMode, props.testResultVisible]);
};

export const useIsGraphEditable = (props: PipelineEditorProps) => {
  const graphMode = useGraphMode(props);

  const graphStatusIsEditable =
    props?.graphVersion && [GRAPH_STATUS.NEW, GRAPH_STATUS.DRAFT].includes(props.graphVersion.toUpperCase() as any);

  const graphModeIsEditable = graphMode === GRAPH_MODE.DEFAULT || graphMode === GRAPH_MODE.DRAG_SELECT;
  return graphStatusIsEditable && graphModeIsEditable;
};
