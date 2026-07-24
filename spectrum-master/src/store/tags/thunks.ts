import { SyncariThunkDispatch } from 'hooks/redux';
import { PromiseThunkAction } from 'store/types';
import { get, post } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { getResponseTagsLike } from 'utils/TagUtil';
import { replaceToken } from 'utils/UrlUtil';

import {
  addTagFailed,
  addTagFulfilled,
  addTagPending,
  getTagsFailed,
  getTagsFulfilled,
  getTagsPending,
  removeTagFailed,
  removeTagFulfilled,
  removeTagPending,
} from './actions';

/**
 * Get the list of connectors that are available
 */
export function getTagsLike(partialName: string): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    const params = { partialName };

    dispatch(getTagsPending(params));

    return get(replaceToken(DataUrlConstants.TAG, params))
      .then((resp) => {
        dispatch(getTagsFulfilled(params, getResponseTagsLike(resp.data)));
      })
      .catch((error) => {
        dispatch(getTagsFailed(params, error));
      });
  };
}

// TODO: better types!
interface AddTagParams {
  type: any;
  objectId: string;
  newTag: string;
}

export function addTag({ type, objectId, newTag }: AddTagParams): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    const params = [
      {
        name: newTag,
        taggedId: objectId,
        type,
      },
    ];

    dispatch(addTagPending(params));

    return post(DataUrlConstants.ADD_TAG, params)
      .then((resp) => {
        dispatch(addTagFulfilled(params, resp.data));
      })
      .catch((error) => {
        dispatch(addTagFailed(params, error));
      });
  };
}

interface RemoveTagParams {
  type: any;
  objectId: string;
  removedTag: string;
}

export function removeTag({ type, objectId, removedTag }: RemoveTagParams): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    const params = [
      {
        name: removedTag,
        taggedId: objectId,
        type,
      },
    ];

    dispatch(removeTagPending(params));

    return post(DataUrlConstants.REMOVE_TAG, params)
      .then((resp) => {
        dispatch(removeTagFulfilled(params, resp.data));
      })
      .catch((error) => {
        dispatch(removeTagFailed(params, error));
      });
  };
}
