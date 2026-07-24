//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// Application level handlers
//
import { navigate } from '@reach/router';
import produce, { Draft } from 'immer';

import { appendAction, isApplicationError, isGatewayTimeoutError, isResourceNotFound } from 'utils/AppUtil';
import RouteConstants from 'utils/RouteConstants';

import {
  APPLICATION_ERROR,
  AppState,
  CLEAR_ERROR_MESSAGE,
  ErrorMessage,
  getApplicationErrorMessage,
  NAVIGATING_TO,
  PHONE_HOME_FAILED,
  PHONE_HOME_FULFILLED,
  PHONE_HOME_PENDING,
  SET_GLOBAL_JS_ERROR,
  SET_CHANGES_IN_PROGRESS,
  SET_CHANGES_IN_PROGRESS_MODAL,
  SET_NODE_FOR_KEBAB_MENU,
  CLEAR_NODE_FOR_KEBAB_MENU,
} from './app.types';

export function _getDefaultState(): AppState {
  return {
    changesInProgress: false,
    changesInProgressModal: {
      visible: false,
      variant: null,
      discardChangesAction: () => null,
      keepEditingAction: () => null,
    },
    errorMessage: null,
    errorTitle: null,
    globalErrorMessage: null,
    globalErrorStack: null,
    lastActions: [],
    navigatingTo: null,
    phoneHomeErrorMessage: null,
    phoneHomeId: null,
    sendingPhoneHome: false,
  };
}

const appReducer = produce((draft: Draft<AppState>, action) => {
  switch (action.type) {
    case CLEAR_ERROR_MESSAGE:
      draft.errorMessage = null;
      draft.errorTitle = null;
      break;
    case NAVIGATING_TO:
      draft.navigatingTo = action.url;
      break;
    case SET_GLOBAL_JS_ERROR:
      draft.globalErrorMessage = action.globalErrorMessage;
      draft.globalErrorStack = action.globalErrorStack;
      break;
    case SET_NODE_FOR_KEBAB_MENU:
      draft.kebabMenuNode = action.kebabMenuNode;
      break;
    case CLEAR_NODE_FOR_KEBAB_MENU:
      delete draft.kebabMenuNode;
      break;
    case PHONE_HOME_PENDING:
      draft.phoneHomeId = action.phoneHomeId;
      draft.sendingPhoneHome = true;
      break;
    case PHONE_HOME_FULFILLED:
      draft.sendingPhoneHome = false;
      break;
    case PHONE_HOME_FAILED:
      draft.sendingPhoneHome = false;
      draft.phoneHomeErrorMessage = action.error?.errorMessage;
      break;
    case APPLICATION_ERROR:
      const err = action?.applicationError;
      draft.lastActions = appendAction(draft.lastActions, action);

      if (err) {
        if (isGatewayTimeoutError(err)) {
          navigate(RouteConstants.ERROR_504);
        } else if (isResourceNotFound(err)) {
          draft.errorMessage = err.message;
          draft.errorTitle = getApplicationErrorMessage(ErrorMessage.resourceNotFound);
        } else if (isApplicationError(err)) {
          draft.errorMessage = err.message;
          draft.errorTitle = getApplicationErrorMessage(ErrorMessage.applicationError);
        }
      }
      break;
    case SET_CHANGES_IN_PROGRESS:
      draft.changesInProgress = action.payload.changesInProgress;
      break;
    case SET_CHANGES_IN_PROGRESS_MODAL:
      if (action.payload.visible) {
        draft.changesInProgress = false;
      }
      draft.changesInProgressModal = {
        visible: action.payload.visible,
        variant: action.payload.variant,
        discardChangesAction: action.payload.discardChangesAction,
        keepEditingAction: action.payload.keepEditingAction,
      };
      break;
    default:
      return draft;
  }
}, _getDefaultState());

export default appReducer;
