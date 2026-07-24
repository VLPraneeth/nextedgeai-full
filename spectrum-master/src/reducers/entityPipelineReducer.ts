// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import produce from 'immer';
import { findIndex, forEach } from 'lodash';
import moment from 'moment';

import { ActionTypes } from 'actions/entityPipelineActions';
import AppConstants from 'utils/AppConstants';
import { SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';

const t = tNamespaced('PipelineEditor');

const { FETCH_STATUS } = AppConstants;

export function _getDefaultState() {
  return {
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.ENTITY_PIPELINE),
    entityPipeline: {},
    entityPipelineApproving: false,
    entityPipelineApprovingErrorMsg: '',
    entityPipelineDeleting: false,
    entityPipelineDiscarding: false,
    entityPipelineError: null,
    entityPipelineExists: undefined,
    entityPipelineFetching: false,
    entityPipelineSaved: false,
    entityPipelineSaving: false,
    entityPipelineValidated: false,
    entityPipelineValidating: false,
    schemas: {},
    nodeConfigModalVisible: false,
    errorTitle: '',
    fieldDraftSummary: {},
    fieldDraftSummaryFetching: false,
    connectorEntities: [],
    connectorEntitiesFetching: false,
    displayedGraph: null,
    errorMessage: '',
    errorMsg: '', // TODO: Should this be `errorMessage`?
    dynamicConfig: null,
    dynamicConfigLoading: false,
    dynamicConfigValues: {},
    dynamicConfigStatus: {},
    dynamicConfigErrorMessage: {},
    graphId: null,
    groupConfiguration: null,
    lastSyncedTime: null,
    pipelineContext: null,
    publishDraftModalEntityId: null,
    publishDraftModalVisible: false,
    deleteDraftModalVisible: false,
    savedNodeConfig: null,
    selectedGraphNode: null,
    resyncDetails: {},

    showingResyncModal: false,
    showingResyncDraftWarningModal: false,
    requestingResyncStatus: AppConstants.FETCH_STATUS.IDLE,
    requestingResyncError: null,
    getSyncStatusStatus: FETCH_STATUS.IDLE,
    getSyncStatusErrorMessage: '',
    pipelineTransitioning: {
      id: null,
      type: null,
      status: FETCH_STATUS.IDLE,
      toast: false,
    },
  };
}

const reducer = produce((draft, action) => {
  switch (action.type) {
    case ActionTypes.GET_ENTITY_PIPELINE_PENDING:
      draft.entityPipelineError = undefined;
      draft.entityPipelineFetching = true;
      break;
    case ActionTypes.GET_ENTITY_PIPELINE_FULFILLED:
      draft.entityPipeline = action.payload;
      draft.entityPipelineError = undefined;
      draft.entityPipelineExists = true;
      draft.entityPipelineFetching = false;
      draft.lastSyncedTime = action.payload.lastSyncedTime;
      draft.pausedBy = action.payload.pausedBy;
      break;
    case ActionTypes.GET_ENTITY_PIPELINE_FAILED:
      draft.entityPipeline = {};
      draft.entityPipelineExists = action.exists;
      draft.entityPipelineFetching = false;
      draft.entityPipelineError = action.error;
      break;
    case ActionTypes.GET_ENTITY_SCHEMA_FULFILLED:
      draft.schemas[action.connectorId] = action.payload;
      break;
    case ActionTypes.CLEAR_ENTITY_PIPELINE:
      draft.entityPipeline = {};
      draft.entityPipelineError = null;
      draft.requestingResyncStatus = FETCH_STATUS.IDLE;
      break;
    case ActionTypes.CLEAR_CONNECTOR_ENTITIES:
      draft.connectorEntities = [];
      break;
    case ActionTypes.GET_CONNECTOR_ENTITIES_FOR_PIPELINE_PENDING:
      draft.connectorEntitiesFetching = true;
      break;
    case ActionTypes.GET_CONNECTOR_ENTITIES_FOR_PIPELINE_FULFILLED:
      draft.connectorEntitiesFetching = false;
      draft.connectorEntities = action.payload;
      break;
    case ActionTypes.GET_CONNECTOR_ENTITIES_FOR_PIPELINE_FAILED:
      draft.connectorEntitiesFetching = false;
      break;
    case ActionTypes.SHOW_NODE_CONFIG:
      draft.nodeConfigModalVisible = action.visible;
      draft.nodeConfigContext = action.visible ? action.context : null;
      draft.nodeConfigName = action.name;
      break;
    case ActionTypes.SAVE_ENTITY_PIPELINE_PENDING:
      draft.entityPipelineSaving = true;
      draft.entityPipelineSaved = false;
      break;
    case ActionTypes.SAVE_ENTITY_PIPELINE_FULFILLED:
      draft.entityPipelineSaving = false;
      draft.lastSyncedTime = action.payload.lastSyncedTime;
      draft.entityPipelineSaved = true;
      break;
    case ActionTypes.SAVE_ENTITY_PIPELINE_FAILED:
      draft.entityPipelineSaving = false;
      draft.entityPipelineSaved = false;
      draft.errorTitle = 'Save Failed';
      draft.errorMessage = action.error.errorMessage;
      break;
    case ActionTypes.SYNC_SUCCESS:
      if (draft.entityPipeline?.targetId === action.payload.targetId) {
        const syncTime = moment(action.payload.lastSuccessfulSync).format(SHORT_DATE_TIME_FORMAT);
        draft.entityPipeline.readOnlyReason = `Last Synced: ${syncTime}`;
        draft.lastSyncedTime = syncTime;
      }
      break;
    case ActionTypes.PIPELINE_EVENT:
      if (draft.entityPipeline?.targetId === action.payload.targetId) {
        draft.entityPipeline.syncStatus = action.payload.syncStatus;
      }
      break;
    case ActionTypes.GET_RESYNC_DETAILS_FULFILLED:
      draft.resyncDetails[action.payload.entityId] = action.payload.resyncDetails;
      break;
    case ActionTypes.SET_SELECTED_GRAPH_NODE:
      draft.selectedGraphNode = action.selectedGraphNode;
      break;
    case ActionTypes.SET_NODE_CONFIG:
      draft.savedNodeConfig = action.nodeConfig;
      break;
    case ActionTypes.VALIDATE_ENTITY_PIPELINE_PENDING:
      draft.entityPipelineValidating = true;
      draft.entityPipelineValidated = false;
      break;
    case ActionTypes.VALIDATE_ENTITY_PIPELINE_FULFILLED:
      draft.errorTitle = '';
      draft.errorMessage = '';
      draft.validationErrors = [];
      draft.entityPipelineValidating = false;
      draft.entityPipelineValidated = true;
      break;
    case ActionTypes.VALIDATE_ENTITY_PIPELINE_FAILED:
      draft.errorTitle = t('validation_failed');
      draft.errorMessage = action.error.message;
      draft.validationErrors = action.error.validationErrors ?? [];
      draft.entityPipelineValidating = false;
      draft.entityPipelineValidated = false;
      break;
    case ActionTypes.TEST_ENTITY_PIPELINE_FAILED:
      draft.errorTitle = t('test_failed');
      draft.errorMessage = action.error.message;
      break;
    case ActionTypes.CLEAR_ERROR:
      draft.errorTitle = '';
      draft.errorMessage = '';
      break;
    case ActionTypes.GET_FIELD_DRAFT_SUMMARY_PENDING:
      draft.fieldDraftSummaryFetching = true;
      break;
    case ActionTypes.GET_FIELD_DRAFT_SUMMARY_FULFILLED:
      draft.fieldDraftSummaryFetching = false;
      draft.fieldDraftSummary[action.entityId] = action.payload.filter(
        (draftItem) => draftItem.ready || draftItem.hasChanges
      );
      break;
    case ActionTypes.GET_FIELD_DRAFT_SUMMARY_FAILED:
      draft.fieldDraftSummaryFetching = false;
      break;
    case ActionTypes.SHOW_PUBLISH_DRAFT_MODAL:
      draft.publishDraftModalVisible = action.visible;
      draft.publishDraftModalEntityId = action.publishDraftModalEntityId;
      draft.hasUnpublishedSynapse = action.hasUnpublishedSynapse;
      break;
    case ActionTypes.SHOW_DELETE_DRAFT_MODAL:
      draft.deleteDraftModalVisible = action.visible;
      draft.deleteDraftModalEntityId = action.deleteDraftModalEntityId;
      draft.deleteDraftModalRefreshOnDelete = action.deleteDraftModalRefreshOnDelete;
      break;
    case ActionTypes.SHOW_CLONE_PIPLINE_MODAL:
      draft.clonePipelineModalVisible = action.visible;
      draft.clonePipelineEntityId = action.entityId;
      draft.clonePipelineIsDraft = action.isDraft;
      draft.clonePipelineName = action.pipelineName;
      break;
    case ActionTypes.DISCARD_ENTITY_PIPELINE_PENDING:
      draft.entityPipelineDiscarding = true;
      break;
    case ActionTypes.DISCARD_ENTITY_PIPELINE_FULFILLED:
      draft.entityPipelineDiscarding = false;
      break;
    case ActionTypes.DISCARD_ENTITY_PIPELINE_FAILED:
      draft.entityPipelineDiscarding = false;
      break;
    case ActionTypes.DELETE_ENTITY_PIPELINE_PENDING:
      draft.entityPipelineDeleting = true;
      break;
    case ActionTypes.DELETE_ENTITY_PIPELINE_FULFILLED:
      draft.entityPipelineDeleting = false;
      break;
    case ActionTypes.DELETE_ENTITY_PIPELINE_FAILED:
      draft.entityPipelineDeleting = false;
      break;
    case ActionTypes.CREATE_DRAFT_ENTITY_PIPELINE_PENDING:
      draft.entityPipelineError = null;
      draft.creatingDraftEntityPipeline = true;
      break;
    case ActionTypes.CREATE_DRAFT_ENTITY_PIPELINE_FULFILLED:
      draft.entityPipelineError = null;
      draft.creatingDraftEntityPipeline = false;
      break;
    case ActionTypes.CREATE_DRAFT_ENTITY_PIPELINE_FAILED:
      draft.fieldPipelineError = action.error;
      draft.creatingDraftFieldPipeline = false;
      break;
    case ActionTypes.APPROVE_ENTITY_PIPELINE_PENDING:
      draft.entityPipelineApprovingErrorMsg = '';
      draft.entityPipelineApproving = true;
      break;
    case ActionTypes.APPROVE_ENTITY_PIPELINE_FULFILLED:
      draft.entityPipelineApprovingErrorMsg = '';
      draft.entityPipelineApproving = false;
      break;
    case ActionTypes.APPROVE_ENTITY_PIPELINE_FAILED:
      draft.entityPipelineApprovingErrorMsg = action.error.errorMessage;
      draft.entityPipelineApproving = false;
      break;
    case ActionTypes.INITIALIZE_APPROVE_MODAL:
      draft.entityPipelineApprovingErrorMsg = '';
      draft.entityPipelineApproving = false;
      break;
    case ActionTypes.SET_PIPELINE_CONTEXT:
      draft.pipelineContext = action.pipelineContext;
      break;
    case ActionTypes.GET_ASYNC_NODE_CONFIG_PENDING:
      draft.dynamicConfigStatus[action.nodeId] = AppConstants.FETCH_STATUS.LOADING;
      draft.dynamicConfigValues[action.nodeId] = null;
      draft.dynamicConfigErrorMessage[action.nodeId] = null;
      break;
    case ActionTypes.GET_ASYNC_NODE_CONFIG_FULFILLED:
      draft.dynamicConfigStatus[action.nodeId] = AppConstants.FETCH_STATUS.SUCCESS;
      draft.dynamicConfigValues[action.nodeId] = action.payload;
      draft.dynamicConfigErrorMessage[action.nodeId] = null;
      break;
    case ActionTypes.GET_ASYNC_NODE_CONFIG_FAILED:
      draft.dynamicConfigValues[action.nodeId] = null;
      draft.dynamicConfigStatus[action.nodeId] = AppConstants.FETCH_STATUS.ERROR;
      draft.dynamicConfigErrorMessage[action.nodeId] = action.error?.errorMessage;
      break;
    case ActionTypes.CLEAR_DYNAMIC_CONFIG:
      draft.dynamicConfig = undefined;
      draft.dynamicConfigValues = {};
      draft.dynamicConfigStatus = {};
      draft.dynamicConfigErrorMessage = {};
      break;
    case ActionTypes.SET_GROUP_CONFIGURATION:
      draft.groupConfiguration = action.groupConfiguration;
      break;
    case ActionTypes.TEST_PIPELINE_DONE:
      draft.liveTestGraphId = action.payload.targetId;
      draft.liveTestCompletedTimestamp = Date.now();
      break;
    case ActionTypes.RESYNC_ENTITY_SOURCES_PENDING:
      draft.requestingResyncStatus = AppConstants.FETCH_STATUS.LOADING;
      draft.requestingResyncError = null;
      break;
    case ActionTypes.TEST_ENTITY_PIPELINE_PENDING:
      if (draft.entityPipeline?.draft?.readOnly === false) {
        draft.entityPipeline.draft.readOnly = true;
      }
      break;
    case ActionTypes.RESYNC_ENTITY_SOURCES_FULFILLED:
      draft.requestingResyncStatus = AppConstants.FETCH_STATUS.SUCCESS;
      break;
    case ActionTypes.RESYNC_ENTITY_SOURCES_FAILED:
      draft.requestingResyncStatus = AppConstants.FETCH_STATUS.ERROR;
      draft.requestingResyncError = action?.error?.errorMessage;
      break;
    case ActionTypes.SHOW_RESYNC_DRAFT_WARNING_MODAL:
      draft.showingResyncDraftWarningModal = action.payload.isVisible;
      break;
    case ActionTypes.SHOW_RESYNC_ENTITY_MODAL:
      draft.showingResyncModal = action.payload.isVisible;
      // Reset before showing
      if (action.payload.isVisible) {
        draft.requestingResyncStatus = AppConstants.FETCH_STATUS.IDLE;
        draft.requestingResyncError = null;
      }
      break;
    case ActionTypes.GET_SYNC_STATUS_PENDING:
      draft.getSyncStatusStatus = FETCH_STATUS.LOADING;
      draft.entitySyncStatus = null;
      draft.entityIdSyncStatus = action.entityId;
      draft.getSyncStatusErrorMessage = '';
      break;
    case ActionTypes.GET_SYNC_STATUS_FULFILLED:
      if (draft.entityIdSyncStatus === action.entityId) {
        draft.getSyncStatusStatus = FETCH_STATUS.SUCCESS;
        draft.entitySyncStatus = action.payload;
        draft.getSyncStatusErrorMessage = '';

        // Update the entitySyncStatuses with the details for this specific entity
        const entityIndexInGroup = findIndex(draft.entitySyncStatuses, { syncariEntityId: action.entityId });

        if (entityIndexInGroup !== undefined) {
          forEach(action.payload, (value, key) => {
            draft.entitySyncStatuses[entityIndexInGroup][key] = value;
          });
        }
      }
      break;
    case ActionTypes.GET_SYNC_STATUS_FAILED:
      if (draft.entityIdSyncStatus === action.entityId) {
        draft.getSyncStatusStatus = FETCH_STATUS.ERROR;
        draft.entitySyncStatus = null;
        draft.getSyncStatusErrorMessage = action.error?.message || action.error?.errorMessage;
      }
      break;
    case ActionTypes.GET_SYNC_STATUSES_PENDING:
      draft.getSyncStatusesStatus = FETCH_STATUS.LOADING;
      draft.syncStatusesErrorMessage = '';
      break;
    case ActionTypes.GET_SYNC_STATUSES_FULFILLED:
      draft.getSyncStatusesStatus = FETCH_STATUS.SUCCESS;
      draft.entitySyncStatuses = action.payload;
      draft.syncStatusesErrorMessage = '';
      break;
    case ActionTypes.GET_SYNC_STATUSES_FAILED:
      draft.getSyncStatusesStatus = FETCH_STATUS.ERROR;
      draft.entitySyncStatuses = null;
      draft.syncStatusesErrorMessage = action.error?.message || action.error?.errorMessage;
      break;
    case ActionTypes.SET_GRAPH_FOR_PUBLISH_READY_ONLY:
      draft.graphForPublishReadyOnly = action.graph;
      break;

    // STARTING
    case ActionTypes.START_ENTITY_PIPELINE_PENDING:
      draft.pipelineTransitioning = {
        id: action.entityId,
        type: 'resume',
        status: FETCH_STATUS.LOADING,
        toast: false,
      };
      break;
    case ActionTypes.START_ENTITY_PIPELINE_FAILED:
      draft.pipelineTransitioning = {
        id: action.entityId,
        type: 'resume',
        status: FETCH_STATUS.ERROR,
        toast: true,
      };
      break;
    case ActionTypes.START_ENTITY_PIPELINE_FULFILLED:
      draft.pipelineTransitioning = {
        id: action.entityId,
        type: 'resume',
        status: FETCH_STATUS.SUCCESS,
        toast: true,
      };
      break;

    // STOPPING
    case ActionTypes.STOP_ENTITY_PIPELINE_PENDING:
      draft.pipelineTransitioning = {
        id: action.entityId,
        type: 'pause',
        status: FETCH_STATUS.LOADING,
        toast: false,
      };
      break;
    case ActionTypes.STOP_ENTITY_PIPELINE_FAILED:
      draft.pipelineTransitioning = {
        id: action.entityId,
        type: 'pause',
        status: FETCH_STATUS.ERROR,
        toast: true,
      };
      break;
    case ActionTypes.STOP_ENTITY_PIPELINE_FULFILLED:
      draft.pipelineTransitioning = {
        id: action.entityId,
        type: 'pause',
        status: FETCH_STATUS.SUCCESS,
        toast: true,
      };
      break;

    // RESET
    case ActionTypes.STOP_TRANSITIONING_PIPELINE_TOAST:
      draft.pipelineTransitioning = {
        ...draft.pipelineTransitioning,
        toast: false,
      };
      break;
    case ActionTypes.RESET_TRANSITIONING_PIPELINE:
      draft.pipelineTransitioning = {
        id: null,
        type: null,
        status: FETCH_STATUS.IDLE,
        toast: false,
      };
      break;

    default:
      return;
  }
}, _getDefaultState());

export default reducer;
