//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { SyncariThunkDispatch } from 'hooks/redux';
import { get, post } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { getResponseTagsLike } from 'utils/TagUtil';
import { replaceToken } from 'utils/UrlUtil';

// Action types that are dispatched by this action
// prettier-ignore
export const ActionTypes = {
  GET_TAGS_PENDING    : 'GET_TAGS_PENDING',
  GET_TAGS_FULFILLED  : 'GET_TAGS_FULFILLED',
  GET_TAGS_FAILED     : 'GET_TAGS_FAILED',

  ADD_TAG_PENDING   : 'ADD_TAG_PENDING',
  ADD_TAG_FULFILLED : 'ADD_TAG_FULFILLED',
  ADD_TAG_FAILED    : 'ADD_TAG_FAILED',

  REMOVE_TAG_PENDING   : 'REMOVE_TAG_PENDING',
  REMOVE_TAG_FULFILLED : 'REMOVE_TAG_FULFILLED',
  REMOVE_TAG_FAILED    : 'REMOVE_TAG_FAILED',

};

/**
 * Get the list of connectors that are available
 */
export function getTagsLike(partialName: string) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_TAGS_PENDING,
    });

    const params = { partialName: partialName ? btoa(partialName) : partialName };

    return get(replaceToken(DataUrlConstants.TAG, params))
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_TAGS_FULFILLED,
          payload: getResponseTagsLike(resp.data),
          params,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_TAGS_FAILED,
          error,
          params,
        });
      });
  };
}

export function addTag({ type, objectId, newTag }: any) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.ADD_TAG_PENDING,
    });

    const params = [
      {
        name: newTag,
        taggedId: objectId,
        type,
      },
    ];

    if (params.length > 0) {
      return post(DataUrlConstants.ADD_TAG, params)
        .then((resp) => {
          dispatch({
            type: ActionTypes.ADD_TAG_FULFILLED,
            payload: resp.data,
            params,
          });
        })
        .catch((error) => {
          dispatch({
            type: ActionTypes.ADD_TAG_FAILED,
            error,
            params,
          });
        });
    }
  };
}

export function removeTag({ type, objectId, removedTag }: any) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.REMOVE_TAG_PENDING,
    });

    const params = [
      {
        name: removedTag,
        taggedId: objectId,
        type,
      },
    ];

    if (params.length > 0) {
      return post(DataUrlConstants.REMOVE_TAG, params)
        .then((resp) => {
          dispatch({
            type: ActionTypes.REMOVE_TAG_FULFILLED,
            payload: resp.data,
            params,
          });
        })
        .catch((error) => {
          dispatch({
            type: ActionTypes.REMOVE_TAG_FAILED,
            error,
            params,
          });
        });
    }
  };
}
