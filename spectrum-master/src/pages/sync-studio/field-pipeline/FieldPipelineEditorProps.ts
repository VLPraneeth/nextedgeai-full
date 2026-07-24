//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { bindActionCreators } from 'redux';

import { getConnectors } from 'actions/connectorActions';
import {
  getEntityPipeline,
  setPipelineContext,
  setSelectedGraphNode,
  showNodeConfigModal,
} from 'actions/entityPipelineActions';
import {
  approveFieldPipeline,
  clearAttributeNodes,
  clearError,
  clearFieldPipeline,
  createDraftFieldPipeline,
  deleteFieldPipeline,
  discardFieldPipeline,
  getAttributeNodes,
  getFieldPipeline,
  showFieldPipelineError,
  updateFieldPipeline,
  validate,
} from 'actions/fieldPipelineActions';
import { SyncariThunkDispatch } from 'hooks/redux';
import { getDervConnectors } from 'selectors/connectorSelectors';
import { setNavigatingTo } from 'store/app/actions';
import { getEntities } from 'store/entity/actions';
import { selectAttributeNodesWithMeta } from 'store/field-pipeline/selectors';
import {
  clearNodeCheckValues,
  deleteFragment,
  enableNodeCheck,
  hideFragment,
  resetFragmentModal,
  saveFragment,
  setNodeCheck,
  showCreateFragmentModal,
  showFragment,
  showShareFragmentModal,
} from 'store/fragment/actions';
import { getFieldPipelineActions } from 'store/pipeline-actions';
import { getFieldPipelineFunctions } from 'store/pipeline-functions';
import {
  graphChanged,
  setCurrentGraph,
  setDisplayedGraph,
  setPipelineId,
  showDeleteMultipleNodesModal,
  showUnsavedConfirmModal,
} from 'store/pipeline/actions';
import { selectFieldPipeline } from 'store/pipeline/selectors';
import { setTestPanelView, showCreateTest } from 'store/test/actions';
import { selectCreateTestVisible, selectTestResultVisible } from 'store/test/selectors';
import { getUserPreference, setUserPreference } from 'store/user/thunks';
import { setIsGotoBetweenFieldPipelines, showValidationResultsPanel } from 'store/validation/slice';

import { RootState } from '../../../reducers/index';
import { PipelineEditorProps } from '../pipeline/PipelineEditor.types';

export const mapStateToPropsFieldPipeline = (
  state: RootState,
  props: PipelineEditorProps
): Partial<PipelineEditorProps> => {
  return {
    attributeNodes: selectAttributeNodesWithMeta(state),
    attributeNodesFetching: state.fieldPipeline.attributeNodesFetching,
    changed: state.pipeline.changed,
    changedId: state.pipeline.changedId,
    changedScope: state.pipeline.changedScope,
    connectors: getDervConnectors(state),
    createFragmentVisible: state.fragment.createFragmentVisible,
    createTestVisible: selectCreateTestVisible(state),
    dragSelectMode: state.pipeline.dragSelectMode,
    pipelineViewportMatrix: props.fieldId
      ? state.user.userPref?.syncStudio?.pipelineViewports?.[props.fieldId]
      : undefined,
    deleteFragmentErrorMessage: state.fragment.deleteFragmentErrorMessage,
    deleteFragmentStatus: state.fragment.deleteFragmentStatus,
    deleteMultipleNodesModalVisible: state.pipeline.deleteMultipleNodesModalVisible,
    displayedGraph: state.pipeline.displayedGraph,
    entities: state.entity.entities,
    pipeline: selectFieldPipeline(state),
    pipelineCreating: state.fieldPipeline.creatingDraftFieldPipeline,
    pipelineDeleting: state.fieldPipeline.fieldPipelineDeleting,
    pipelineDiscarding: state.fieldPipeline.fieldPipelineDiscarding,
    pipelineExists: state.fieldPipeline.fieldPipelineExists,
    pipelineFetching: state.fieldPipeline.fieldPipelineFetching,
    pipelineSaved: state.fieldPipeline.fieldPipelineSaved,
    pipelineSaving: state.fieldPipeline.fieldPipelineSaving || state.entityPipeline.entityPipelineSaving,
    pipelineValidated: state.fieldPipeline.fieldPipelineValidated,
    pipelineValidating: state.fieldPipeline.fieldPipelineValidating,
    errorMessage: state.fieldPipeline.errorMessage,
    errorTitle: state.fieldPipeline.errorTitle,
    fragments: state.fragment.fragments,
    fragmentSaving: state.fragment.fragmentSaving,
    hideFragmentErrorMessage: state.fragment.hideFragmentErrorMessage,
    hideFragmentStatus: state.fragment.hideFragmentStatus,
    isGotoBetweenFieldPipelines: state.validation.isGotoBetweenFieldPipelines,
    nodeCheckId: state.fragment.nodeCheckId,
    nodeCheckMode: state.fragment.nodeCheckMode,
    nodeCheckValue: state.fragment.nodeCheckValue,
    nodeCheckValues: state.fragment.nodeCheckValues,
    nodeConfigModalVisible: state.entityPipeline.nodeConfigModalVisible,
    pipelineActions: state.pipelineAction.fieldPipelineActions,
    pipelineActionsFetching: state.pipelineAction.fieldPipelineActionsFetching,
    pipelineFunctions: state.pipelineFunction.fieldPipelineFunctions,
    pipelineFunctionsFetching: state.pipelineFunction.fieldPipelineFunctionsFetching,
    savedNodeConfig: state.entityPipeline.savedNodeConfig,
    saveFragmentErrorMessage: state.fragment.saveFragmentErrorMessage,
    schemas: state.entityPipeline.schemas,
    selectedTestNodeId: state.test.selectedTestNodeId,
    showFragmentErrorMessage: state.fragment.showFragmentErrorMessage,
    showFragmentStatus: state.fragment.showFragmentStatus,
    testPanelView: state.test.testPanelView,
    testResultVisible: selectTestResultVisible(state),
    validationErrors: state.validation.errors,
    validationResultsPanelVisible: state.validation.validationResultsPanelVisible,
    validationWarnings: state.validation.warnings,
  };
};

export const mapDispatchToPropsFieldPipeline = (dispatch: SyncariThunkDispatch) => {
  return bindActionCreators(
    {
      approveFieldPipeline,
      clearAttributeNodes,
      clearError,
      clearNodeCheckValues,
      clearPipeline: clearFieldPipeline,
      createDraftFieldPipeline,
      deleteFieldPipeline,
      deleteFragment,
      discardPipeline: discardFieldPipeline,
      enableNodeCheck,
      getAttributeNodes,
      getConnectors,
      getEntities,
      getPipelineActions: getFieldPipelineActions,
      getPipeline: getFieldPipeline,
      getEntityPipeline,
      getPipelineFunctions: getFieldPipelineFunctions,
      getUserPreference,
      graphChanged,
      hideFragment,
      resetFragmentModal,
      saveFragment,
      setCurrentGraph,
      setDisplayedGraph,
      setIsGotoBetweenFieldPipelines,
      setNavigatingTo,
      setNodeCheck,
      setPipelineContext,
      setPipelineId,
      setSelectedGraphNode,
      setTestPanelView,
      setUserPreference,
      showCreateFragmentModal,
      showCreateTest,
      showDeleteMultipleNodesModal,
      showPipelineError: showFieldPipelineError,
      showFragment,
      showNodeConfigModal,
      showShareFragmentModal,
      showUnsavedConfirmModal,
      showValidationResultsPanel,
      updatePipeline: updateFieldPipeline,
      validate,
    },
    dispatch
  );
};
