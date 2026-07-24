//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { ActionTypes } from 'actions/specterActions';

function _getDefaultState() {
  return {
    resettingSub: false,
    enableSpecterDebuggingStatus: '',
  };
}

export default function specterReducer(state = _getDefaultState(), action: any) {
  switch (action.type) {
    case ActionTypes.SET_OAUTH_PENDING:
      return {
        ...state,
        resettingSub: true,
      };
    case ActionTypes.SET_OAUTH_FULFILLED:
      return {
        ...state,
        resettingSub: false,
        enableSpecterDebuggingStatus: action.enableSpecterDebuggingStatus,
      };
    case ActionTypes.SET_OAUTH_FAILED:
      return {
        ...state,
        resettingSub: false,
      };
    default:
      return state;
  }
}
