// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { ActionTypes } from 'actions/fieldPipelineActions';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';

const { FETCH_STATUS } = AppConstants;

const tn = tNamespaced('PipelineEditor');

function _getDefaultState() {
  return {
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.FIELD_PIPELINE),
    fieldPipeline: null,
    errorTitle: '',
    errorMessage: '',
    validationErrors: [],
    fieldPipelineFetching: false,
    attributeNodesFetching: false,
    attributeNodes: [],
  };
}

export default function pipelineFunctionReducer(state = _getDefaultState(), action) {
  switch (action.type) {
    case ActionTypes.GET_FIELD_PIPELINE_PENDING:
      return {
        ...state,
        fieldPipelineExists: undefined,
        fieldPipelineError: undefined,
        fieldPipelineFetching: true,
      };
    case ActionTypes.GET_FIELD_PIPELINE_FULFILLED:
      return {
        ...state,
        fieldPipelineExists: true,
        fieldPipeline: action.payload,
        fieldPipelineError: undefined,
        fieldPipelineFetching: false,
      };
    case ActionTypes.GET_FIELD_PIPELINE_FAILED:
      return {
        ...state,
        fieldPipeline: {},
        fieldPipelineExists: action.exists,
        fieldPipelineError: action.error?.errorMessage,
        fieldPipelineFetching: false,
      };
    case ActionTypes.GET_ATTRIBUTE_NODES_PENDING:
      return {
        ...state,
        attributeNodesFetching: true,
      };
    case ActionTypes.GET_ATTRIBUTE_NODES_FULFILLED:
      return {
        ...state,
        attributeNodesFetching: false,
        attributeNodes: action.payload,
      };
    case ActionTypes.GET_ATTRIBUTE_NODES_FAILED:
      return {
        ...state,
        attributeNodesFetching: false,
        attributeNodes: [],
      };
    case ActionTypes.VALIDATE_FIELD_PIPELINE_PENDING:
      return {
        ...state,
        fieldPipelineValidating: true,
        fieldPipelineValidated: false,
      };
    case ActionTypes.CREATE_DRAFT_FIELD_PIPELINE_PENDING:
      return {
        ...state,
        fieldPipelineError: null,
        creatingDraftFieldPipeline: true,
      };
    case ActionTypes.CREATE_DRAFT_FIELD_PIPELINE_FULFILLED:
      return {
        ...state,
        fieldPipelineError: null,
        creatingDraftFieldPipeline: false,
      };
    case ActionTypes.CREATE_DRAFT_FIELD_PIPELINE_FAILED:
      return {
        ...state,
        fieldPipelineError: action.error,
        creatingDraftFieldPipeline: false,
      };
    case ActionTypes.VALIDATE_FIELD_PIPELINE_FULFILLED:
      return {
        ...state,
        errorTitle: '',
        errorMessage: '',
        validationErrors: [],
        fieldPipelineValidating: false,
        fieldPipelineValidated: true,
      };
    case ActionTypes.VALIDATE_FIELD_PIPELINE_FAILED:
      return {
        ...state,
        errorTitle: tn('validation_failed'),
        errorMessage: action.error?.message || action.error?.error,
        validationErrors: action.error?.validationErrors ?? [],
        fieldPipelineValidating: false,
        fieldPipelineValidated: false,
      };
    case ActionTypes.CLEAR_ERROR:
      return {
        ...state,
        errorTitle: '',
        errorMessage: '',
      };
    case ActionTypes.CLEAR_FIELD_PIPELINE:
      return {
        ...state,
        fieldPipeline: null,
        fieldPipelineError: null,
        fieldPipelineFetching: false,
      };
    case ActionTypes.CLEAR_ATTRIBUTE_NODES:
      return {
        ...state,
        attributeNodes: [],
      };
    case ActionTypes.DISCARD_FIELD_PIPELINE_PENDING:
      return {
        ...state,
        fieldPipelineDiscarding: true,
      };
    case ActionTypes.DISCARD_FIELD_PIPELINE_FULFILLED:
      return {
        ...state,
        fieldPipelineDiscarding: false,
      };
    case ActionTypes.DISCARD_FIELD_PIPELINE_FAILED:
      return {
        ...state,
        fieldPipelineDiscarding: false,
      };
    case ActionTypes.SAVE_FIELD_PIPELINE_PENDING:
      return {
        ...state,
        fieldPipelineSaving: true,
        fieldPipelineSaved: false,
      };
    case ActionTypes.SAVE_FIELD_PIPELINE_FULFILLED:
      return {
        ...state,
        fieldPipeline: { ...state.fieldPipeline, draft: action.payload },
        fieldPipelineSaving: false,
        fieldPipelineSaved: true,
      };
    case ActionTypes.SAVE_FIELD_PIPELINE_FAILED:
      return {
        ...state,
        fieldPipelineSaving: false,
        fieldPipelineSaved: false,
        errorTitle: tn('save_failed'),
        errorMessage: action.error?.message || action.error?.error,
      };
    case ActionTypes.DELETE_FIELD_PIPELINE_PENDING:
      return {
        ...state,
        fieldPipelineDeleting: true,
      };
    case ActionTypes.DELETE_FIELD_PIPELINE_FULFILLED:
      return {
        ...state,
        fieldPipelineDeleting: false,
      };
    case ActionTypes.DELETE_FIELD_PIPELINE_FAILED:
      return {
        ...state,
        fieldPipelineDeleting: false,
      };
    case ActionTypes.MARK_FIELD_PIPELINE_READY_PENDING:
      return {
        ...state,
        markFieldPipelineReadyErrorMessage: '',
        markFieldPipelineReadyStatus: FETCH_STATUS.LOADING,
      };
    case ActionTypes.MARK_FIELD_PIPELINE_READY_FULFILLED:
      return {
        ...state,
        markFieldPipelineReadyErrorMessage: '',
        markFieldPipelineReadyStatus: FETCH_STATUS.SUCCESS,
      };
    case ActionTypes.MARK_FIELD_PIPELINE_READY_FAILED:
      return {
        ...state,
        markFieldPipelineReadyStatus: FETCH_STATUS.ERROR,
        markFieldPipelineReadyErrorMessage: action.error?.message || action.error?.error,
      };
    case ActionTypes.MARK_FIELD_PIPELINE_NOT_READY_PENDING:
      return {
        ...state,
        markFieldPipelineNotReadyErrorMessage: '',
        markFieldPipelineNotReadyStatus: FETCH_STATUS.LOADING,
      };
    case ActionTypes.MARK_FIELD_PIPELINE_NOT_READY_FULFILLED:
      return {
        ...state,
        markFieldPipelineNotReadyErrorMessage: '',
        markFieldPipelineNotReadyStatus: FETCH_STATUS.SUCCESS,
      };
    case ActionTypes.MARK_FIELD_PIPELINE_NOT_READY_FAILED:
      return {
        ...state,
        markFieldPipelineNotReadyStatus: FETCH_STATUS.ERROR,
        markFieldPipelineNotReadyErrorMessage: action.error?.message || action.error?.error,
      };
    default:
      return state;
  }
}
