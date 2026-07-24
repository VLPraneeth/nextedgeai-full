import { useCallback } from 'react';

import AppConstants from 'utils/AppConstants';

import { PipelineToolbarProps } from '../../PipelineEditor.types';
import { usePipelineEditor } from '../context/PipelineEditorV2.context';
import useEnhancedReactFlow from '../hooks/useEnhancedReactFlow';
import { useSaveablePipeline } from '../hooks/useSaveablePipeline';
import { useIsGraphEditable } from '../PipelineEditorV2.hooks';
import getAvailableVersions from './getAvailableVersions';
import { GraphToolbarPropsV2 } from './PipelineToolbarV2';

const { GRAPH_STATUS } = AppConstants;

const useToolbarProps = (props: PipelineToolbarProps) => {
  // TODO: It would give us more control in the UI to use the lastSyncedTime
  // directly rather than getting that as the readOnlyMsg from the backend.
  // Currently we can't show the correct style as defined in SYN-14364.

  // let lastSyncedTime;
  // if (props.lastSyncedTime) {
  //   lastSyncedTime = moment(props.lastSyncedTime).format(SHORT_DATE_TIME_FORMAT);
  // } else {
  //   lastSyncedTime = tn('not_started');
  // }

  const isEditable = useIsGraphEditable(props);

  const allActionsDisabled = props.nodeCheckMode;

  const { savePipeline } = usePipelineEditor();

  const isDraft = props.displayedGraph !== GRAPH_STATUS.APPROVED;

  const isApproveOnlyGraph = useCallback(() => {
    const { pipeline = {} } = props;
    return pipeline?.draftStatus === GRAPH_STATUS.APPROVED && pipeline?.draft === null;
  }, [props]);

  const reactFlow = useEnhancedReactFlow();

  const getSaveablePipeline = useSaveablePipeline();

  const onValidate = useCallback(() => {
    const { validate, entityId, fieldId, isFieldPipeline, pipeline } = props;

    const saveablePipeline = getSaveablePipeline({
      storedPipeline: pipeline,
      reactFlow,
    });

    validate(isFieldPipeline && fieldId ? fieldId : entityId, saveablePipeline);
  }, [props, reactFlow, getSaveablePipeline]);

  const toolbarProps: GraphToolbarPropsV2 = {
    entityId: props.entityId,
    showSave: isEditable,
    emptyToolbar: props.pipelineExists === false,
    onSaveChanges: savePipeline,
    onCreateVersion: () => props.showCreateVersionModal({ visible: true }),
    onValidate,
    disableSave: !props.hasUnsavedChanges,
    showPublishDraft: false,
    isLoading: false,
    availableVersions: getAvailableVersions(props.pipeline),
    loadingMessage: '',
    showSuccess: false,
    successMessage: '',
    errorTitle: props.errorTitle,
    errorMessage: props.errorMessage,
    allActionsDisabled,
    isDraft,
    showValidate: props.displayedGraph !== GRAPH_STATUS.APPROVED,
    showCreateDraft: isApproveOnlyGraph(),
    updatePipeline: props.updatePipeline,
    pipeline: props.pipeline,
  };

  // if (state.lastAction === ACTIONS.SAVE) {
  //   if (!state.haveUnsavedChanges) {
  //     if (props.pipelineSaving) {
  //       toolbarProps.isLoading = true;
  //       toolbarProps.loadingMessage = tc('saving');
  //     } else if (props.pipelineSaved) {
  //       toolbarProps.showSuccess = true;
  //       toolbarProps.successMessage = tc('saved');
  //     }
  //   }
  // } else if (state.lastAction === ACTIONS.VALIDATE) {
  //   if (props.pipelineValidating) {
  //     toolbarProps.isLoading = true;
  //     toolbarProps.loadingMessage = tc('validating');
  //   } else if (props.pipelineValidated) {
  //     toolbarProps.showSuccess = true;
  //     toolbarProps.successMessage = tn('valid_pipeline');
  //   }
  // }

  // toolbarProps.rightGroup = (
  //   <PipelineEditorMoreActions
  //     {...props}
  //     graphIsReadOnly={isGraphReadOnly()}
  //     isApproveWithDraftGraph={isApproveWithDraftGraph()}
  //     isDraftOnlyGraph={isDraftOnlyGraph()}
  //   />
  // );
  return toolbarProps;
};

export default useToolbarProps;
