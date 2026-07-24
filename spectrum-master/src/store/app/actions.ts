// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { ActionTypes as MsActionTypes } from 'store/middleware/messageStream';
import { post } from 'utils/AjaxUtil';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';

import {
  CLEAR_ERROR_MESSAGE,
  CLEAR_NODE_FOR_KEBAB_MENU,
  NAVIGATING_TO,
  PHONE_HOME_FAILED,
  PHONE_HOME_FULFILLED,
  PHONE_HOME_PENDING,
  SET_CHANGES_IN_PROGRESS,
  SET_CHANGES_IN_PROGRESS_MODAL,
  SET_GLOBAL_JS_ERROR,
  SET_NODE_FOR_KEBAB_MENU,
} from './app.types';

/**
 * Clear the error message thats displayed application wide
 */
export function clearErrorMessage() {
  return {
    type: CLEAR_ERROR_MESSAGE,
  };
}

export function setNavigatingTo(url) {
  return {
    type: NAVIGATING_TO,
    url,
  };
}

export function phoneHome(params) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: PHONE_HOME_PENDING,
    });
    post(DataUrlConstants.PHONE_HOME, params)
      .then((resp) => {
        dispatch({
          type: PHONE_HOME_FULFILLED,
          data: resp?.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: PHONE_HOME_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function setGlobalError(globalErrorMessage, globalErrorStack) {
  return {
    type: SET_GLOBAL_JS_ERROR,
    globalErrorMessage,
    globalErrorStack,
  };
}

export function setNodeForKebabMenu(kebabMenuNode) {
  return {
    type: SET_NODE_FOR_KEBAB_MENU,
    kebabMenuNode,
  };
}

export function clearNodeForKebabMenu() {
  return {
    type: CLEAR_NODE_FOR_KEBAB_MENU,
  };
}

export function connectMessageStream(channelId, userName) {
  return {
    type: MsActionTypes.MESSAGE_STREAM_CONNECT,
    channelId,
    userName,
  };
}

export function setChangesInProgress(changesInProgress) {
  return {
    type: SET_CHANGES_IN_PROGRESS,
    payload: {
      changesInProgress,
    },
  };
}

export function setChangesInProgressModal({
  visible = false,
  variant = undefined || null || '',
  keepEditingAction = () => {},
  discardChangesAction = () => {},
}) {
  return {
    type: SET_CHANGES_IN_PROGRESS_MODAL,
    payload: {
      visible,
      variant,
      keepEditingAction,
      discardChangesAction,
    },
  };
}
