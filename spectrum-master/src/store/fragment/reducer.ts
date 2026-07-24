//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import produce, { Draft } from 'immer';

import AppConstants from 'utils/AppConstants';

import * as ActionTypes from './types';

const { PIPELINE_CONTEXT, FETCH_STATUS } = AppConstants;

export function _getDefaultState(): ActionTypes.FragmentState {
  return {
    shareFragmentModalVisible: false,
    fragmentContext: PIPELINE_CONTEXT.ENTITY,
    createFragmentVisible: false,
    nodeCheckValue: false,
    nodeCheckMode: false,
    fragmentSaving: false,
    nodeCheckValues: {},
    fragmentShares: {},
    saveFragmentErrorMessage: '',
    deleteFragmentStatus: FETCH_STATUS.IDLE,
    hideFragmentStatus: FETCH_STATUS.IDLE,
    showFragmentStatus: FETCH_STATUS.IDLE,
    fragmentSharing: false,
    fragmentSharingErrorMessage: '',
    getFragmentStatus: FETCH_STATUS.IDLE,
  };
}

const reducer = produce((draft: Draft<ActionTypes.FragmentState>, action) => {
  switch (action.type) {
    case ActionTypes.SAVE_FRAGMENT_PENDING:
      draft.fragmentSaving = true;
      draft.saveFragmentErrorMessage = '';
      break;
    case ActionTypes.SAVE_FRAGMENT_FULFILLED:
      draft.fragmentSaving = false;
      draft.saveFragmentErrorMessage = '';
      break;
    case ActionTypes.SAVE_FRAGMENT_FAILED:
      draft.fragmentSaving = true;
      draft.saveFragmentErrorMessage = action.error?.message || action.error?.errorMessage;
      break;
    case ActionTypes.RESET_FRAGMENT_MODAL:
      draft.fragmentSaving = false;
      draft.saveFragmentErrorMessage = '';
      break;
    case ActionTypes.SHOW_SHARE_FRAGMENT:
      draft.shareFragmentModalVisible = action.visible;
      draft.shareFragmentId = action.fragmentId;
      draft.fragmentContext = action.context;
      break;
    case ActionTypes.SHOW_CREATE_FRAGMENT:
      draft.createFragmentVisible = action.visible;
      break;
    case ActionTypes.SET_NODE_CHECK:
      draft.nodeCheckId = action.nodeCheckId;
      draft.nodeCheckValue = action.nodeCheckValue;
      draft.nodeCheckValues[action.nodeCheckId] = action.nodeCheckValue;
      break;
    case ActionTypes.ENABLE_NODE_CHECK:
      draft.nodeCheckMode = action.nodeCheckMode;
      draft.nodeCheckValues = {};
      break;
    case ActionTypes.CLEAR_NODE_CHECK:
      draft.nodeCheckValues = {};
      break;
    case ActionTypes.DELETE_FRAGMENT_PENDING:
      draft.deleteFragmentStatus = FETCH_STATUS.LOADING;
      break;
    case ActionTypes.DELETE_FRAGMENT_FULFILLED:
      draft.deleteFragmentStatus = FETCH_STATUS.SUCCESS;
      break;
    case ActionTypes.DELETE_FRAGMENT_FAILED:
      draft.deleteFragmentStatus = FETCH_STATUS.ERROR;
      draft.deleteFragmentErrorMessage = action.error?.message || action.error?.errorMessage;
      break;
    case ActionTypes.HIDE_FRAGMENT_PENDING:
      draft.hideFragmentStatus = FETCH_STATUS.LOADING;
      break;
    case ActionTypes.HIDE_FRAGMENT_FULFILLED:
      draft.hideFragmentStatus = FETCH_STATUS.SUCCESS;
      break;
    case ActionTypes.HIDE_FRAGMENT_FAILED:
      draft.hideFragmentStatus = FETCH_STATUS.ERROR;
      draft.hideFragmentErrorMessage = action.error?.message || action.error?.errorMessage;
      break;
    case ActionTypes.SHOW_FRAGMENT_PENDING:
      draft.showFragmentStatus = FETCH_STATUS.LOADING;
      break;
    case ActionTypes.SHOW_FRAGMENT_FULFILLED:
      draft.showFragmentStatus = FETCH_STATUS.SUCCESS;
      break;
    case ActionTypes.SHOW_FRAGMENT_FAILED:
      draft.showFragmentStatus = FETCH_STATUS.ERROR;
      draft.showFragmentErrorMessage = action.error?.message || action.error?.errorMessage;
      break;
    case ActionTypes.GET_FRAGMENTS_PENDING:
      draft.getFragmentStatus = FETCH_STATUS.LOADING;
      break;
    case ActionTypes.GET_FRAGMENTS_FULFILLED:
      draft.getFragmentStatus = FETCH_STATUS.SUCCESS;
      draft.fragments = action.fragments;
      break;
    case ActionTypes.GET_FRAGMENT_SHARES_FULFILLED:
      draft.fragmentShares[action.fragmentId] = action.fragmentShares;
      break;
    case ActionTypes.SHARE_FRAGMENT_PENDING:
      draft.fragmentSharing = true;
      draft.fragmentSharingErrorMessage = '';
      break;
    case ActionTypes.SHARE_FRAGMENT_FULFILLED:
      draft.fragmentSharing = false;
      draft.fragmentSharingErrorMessage = '';
      break;
    case ActionTypes.SHARE_FRAGMENT_FAILED:
      draft.fragmentSharing = true;
      draft.fragmentSharingErrorMessage = action.error?.message || action.error?.errorMessage;
      break;
    case ActionTypes.RESET_SHARE_FRAGMENT_MODAL:
      draft.fragmentSharing = false;
      draft.fragmentSharingErrorMessage = '';
      break;
    default:
      return draft;
  }
}, _getDefaultState());

export default reducer;
