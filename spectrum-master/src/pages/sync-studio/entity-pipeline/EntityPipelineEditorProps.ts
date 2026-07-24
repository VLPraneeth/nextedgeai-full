//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { bindActionCreators } from 'redux';

import { getConnectors } from 'actions/connectorActions';
import {
  clearConnectorEntities,
  clearEntityPipeline,
  clearError,
  createDraftEntityPipeline,
  deletePublishedPipeline,
  discardEntityPipeline,
  getAsyncNodeConfig,
  getConnectorEntities,
  getEntityPipeline,
  getSyncStatuses,
  setGraphForPublishReadyOnly,
  setPipelineContext,
  setSelectedGraphNode,
  showDeleteDraftModal,
  showEntityPipelineError,
  showNodeConfigModal,
  showPublishDraftModal,
  start,
  stop,
  updateEntityPipeline,
  validate,
} from 'actions/entityPipelineActions';
import { SyncariThunkDispatch } from 'hooks/redux';
import { getDervConnectors } from 'selectors/connectorSelectors';
import { setNavigatingTo } from 'store/app/actions';
import { selectConnectorEntitiesWithMeta } from 'store/entity-pipeline/selectors';
import { getEntities } from 'store/entity/actions';
import { selectDeleteMappingsResponse, selectSaveMappingsResponse } from 'store/fast-mapper/selectors';
import { showFastMapper } from 'store/fast-mapper/slice';
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
import { getEntityPipelineActions } from 'store/pipeline-actions';
import { getEntityPipelineFunctions } from 'store/pipeline-functions';
import {
  graphChanged,
  setCurrentGraph,
  setDisplayedGraph,
  setPipelineId,
  showDeleteMultipleNodesModal,
  showUnsavedConfirmModal,
} from 'store/pipeline/actions';
import { showCreateVersionModal } from 'store/pipeline/slice';
import { getSchemaForEntity } from 'store/schema/thunks';
import { makeSchemaKey } from 'store/schema/utils';
import { setTestPanelView, showCreateTest } from 'store/test/actions';
import { selectCreateTestVisible, selectTestResultVisible } from 'store/test/selectors';
import { getUserPreference, setUserPreference } from 'store/user/thunks';
import { showValidationResultsPanel } from 'store/validation/slice';

import { RootState } from '../../../reducers/index';
import { PipelineEditorProps } from '../pipeline/PipelineEditor.types';

export const mapStateToPropsEntityPipeline = (
  state: RootState,
  props: PipelineEditorProps
): Partial<PipelineEditorProps> => {
  return {
    changed: state.pipeline.changed,
    changedId: state.pipeline.changedId,
    changedScope: state.pipeline.changedScope,
    connectorEntities: selectConnectorEntitiesWithMeta(state),
    connectorEntitiesFetching: state.entityPipeline.connectorEntitiesFetching,
    connectors: getDervConnectors(state),
    connectorsMetadata: state.connector.connectorsMetadata,
    createFragmentVisible: state.fragment.createFragmentVisible,
    createTestVisible: selectCreateTestVisible(state),
    currentInstanceState: state.instance.currentInstanceState,
    pipelineViewportMatrix: state.user.userPref?.syncStudio?.pipelineViewports?.[props.entityId],
    deleteFragmentErrorMessage: state.fragment.deleteFragmentErrorMessage,
    deleteFragmentStatus: state.fragment.deleteFragmentStatus,
    deleteMappingsResponse: selectDeleteMappingsResponse(state),
    deleteMultipleNodesModalVisible: state.pipeline.deleteMultipleNodesModalVisible,
    displayedGraph: state.pipeline.displayedGraph,
    dragSelectMode: state.pipeline.dragSelectMode,
    entities: state.entity.entities,
    entitiesFetching: state.entity.entitiesFetching,
    entitySchema:
      state.schema.entities[makeSchemaKey({ entityId: props.entityId, graphVersion: props.graphVersion as string })],
    pipeline: state.entityPipeline.entityPipeline,
    pipelineApproving: state.entityPipeline.entityPipelineApproving,
    // The pipelineCreating is only triggered when creating a field pipeline
    // (which can happen from the EP page by clicking the kebab on the FP on in
    // the core node panel)
    // When a EP draft is created the pipelineSaving field is used.
    pipelineCreating: state.fieldPipeline.creatingDraftFieldPipeline,
    pipelineDeleting: state.entityPipeline.entityPipelineDeleting,
    pipelineDiscarding: state.entityPipeline.entityPipelineDiscarding,
    pipelineError: state.entityPipeline.entityPipelineError,
    pipelineExists: state.entityPipeline.entityPipelineExists,
    pipelineFetching: state.entityPipeline.entityPipelineFetching,
    pipelineSaved: state.entityPipeline.entityPipelineSaved,
    pipelineSaving: state.entityPipeline.entityPipelineSaving,
    pipelineValidated: state.entityPipeline.entityPipelineValidated,
    pipelineValidating: state.entityPipeline.entityPipelineValidating,
    errorMessage: state.entityPipeline.errorMessage,
    errorTitle: state.entityPipeline.errorTitle,
    fragments: state.fragment.fragments,
    fragmentSaving: state.fragment.fragmentSaving,
    getFragmentStatus: state.fragment.getFragmentStatus,
    hideFragmentErrorMessage: state.fragment.hideFragmentErrorMessage,
    hideFragmentStatus: state.fragment.hideFragmentStatus,
    lastSyncedTime: state.entityPipeline.lastSyncedTime,
    pausedBy: state.entityPipeline.pausedBy,
    liveTestCompletedTimestamp: state.entityPipeline.liveTestCompletedTimestamp,
    liveTestGraphId: state.entityPipeline.liveTestGraphId,
    nodeCheckId: state.fragment.nodeCheckId,
    nodeCheckMode: state.fragment.nodeCheckMode,
    nodeCheckValue: state.fragment.nodeCheckValue,
    nodeCheckValues: state.fragment.nodeCheckValues,
    nodeConfigModalVisible: state.entityPipeline.nodeConfigModalVisible,
    pipelineActions: state.pipelineAction.entityPipelineActions,
    pipelineActionsFetching: state.pipelineAction.entityPipelineActionsFetching,
    pipelineFunctions: state.pipelineFunction.entityPipelineFunctions,
    pipelineFunctionsFetching: state.pipelineFunction.entityPipelineFunctionsFetching,
    readOnly: state.entityPipeline.readOnly,
    savedNodeConfig: state.entityPipeline.savedNodeConfig,
    saveFragmentErrorMessage: state.fragment.saveFragmentErrorMessage,
    saveMappingsResponse: selectSaveMappingsResponse(state),
    selectedNode: state.entityPipeline.selectedGraphNode,
    selectedTestNodeId: state.test.selectedTestNodeId,
    showFragmentErrorMessage: state.fragment.showFragmentErrorMessage,
    showFragmentStatus: state.fragment.showFragmentStatus,
    syncStatus: state.entityPipeline.syncStatus,
    testPanelView: state.test.testPanelView,
    testResultVisible: selectTestResultVisible(state),
    validationErrors: state.validation.errors,
    validationResultsPanelVisible: state.validation.validationResultsPanelVisible,
    validationWarnings: state.validation.warnings,
  };
};

export const mapDispatchToPropsEntityPipeline = (dispatch: SyncariThunkDispatch) => {
  return bindActionCreators(
    {
      clearConnectorEntities,
      clearError,
      clearNodeCheckValues,
      clearPipeline: clearEntityPipeline,
      createDraftEntityPipeline,
      getSyncStatuses,
      deleteFragment,
      deletePublishedPipeline,
      discardPipeline: discardEntityPipeline,
      enableNodeCheck,
      showDeleteDraftModal,
      getAsyncNodeConfig,
      getConnectorEntities,
      getConnectors,
      getEntities,
      getSchemaForEntity,
      getPipeline: getEntityPipeline,
      getEntityPipeline,
      getPipelineActions: getEntityPipelineActions,
      getPipelineFunctions: getEntityPipelineFunctions,
      getUserPreference,
      graphChanged,
      hideFragment,
      resetFragmentModal,
      saveFragment,
      setCurrentGraph,
      setDisplayedGraph,
      setGraphForPublishReadyOnly,
      setNavigatingTo,
      setNodeCheck,
      setPipelineContext,
      setPipelineId,
      setSelectedGraphNode,
      setTestPanelView,
      setUserPreference,
      showCreateFragmentModal,
      showCreateTest,
      showPipelineError: showEntityPipelineError,
      showCreateVersionModal,
      showDeleteMultipleNodesModal,
      showFastMapper,
      showFragment,
      showNodeConfigModal,
      showPublishDraftModal,
      showShareFragmentModal,
      showUnsavedConfirmModal,
      showValidationResultsPanel,
      start,
      stop,
      updatePipeline: updateEntityPipeline,
      validate,
    },
    dispatch
  );
};
