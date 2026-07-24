//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { SyncariThunkDispatch } from 'hooks/redux';
import { get, post } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/UrlUtil';

export const ActionTypes = {
  SET_OAUTH_PENDING: 'SET_OAUTH_PENDING',
  SET_OAUTH_FULFILLED: 'SET_OAUTH_FULFILLED',
  SET_OAUTH_FAILED: 'SET_OAUTH_FAILED',

  GHOST_LOGIN_PENDING: `GHOST_LOGIN_PENDING`,
  GHOST_LOGIN_FULFILLED: `GHOST_LOGIN_FULFILLED`,
  GHOST_LOGIN_FAILED: `GHOST_LOGIN_FAILED`,
};

export function setOauth(params: any) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.SET_OAUTH_PENDING,
    });

    const url = replaceToken(DataUrlConstants.SET_OAUTH, {
      fromSyncariId: params.fromSyncariId,
      toSyncariId: params.toSyncariId,
    });
    post(url)
      .then((resp) => {
        dispatch({
          type: ActionTypes.SET_OAUTH_FULFILLED,
          enableSpecterDebuggingStatus: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.SET_OAUTH_FAILED,
          payload: error,
        });
      });
  };
}

export function ghostLogin(id: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GHOST_LOGIN_PENDING,
    });

    return get(replaceToken(DataUrlConstants.GHOST_LOGIN, { syncariId: id }))
      .then((resp) => {
        dispatch({
          type: ActionTypes.GHOST_LOGIN_FULFILLED,
        });

        window.location.href = '/';
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GHOST_LOGIN_FAILED,
        });

        (window as any).location = RouteConstants.LOGIN;
      });
  };
}
