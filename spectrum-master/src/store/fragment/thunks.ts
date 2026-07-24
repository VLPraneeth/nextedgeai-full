//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { SyncariThunkDispatch } from 'hooks/redux';
import { PipelineContext } from 'pages/sync-studio/types';
import { PromiseThunkAction } from 'store/types';
import { deleteRequest, get, post, put } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { replaceToken } from 'utils/StringUtil';

import * as ActionTypes from './types';
import { FragmentModel, InstanceIdsModel } from './types';

const { PIPELINE_CONTEXT } = AppConstants;

export function getFragments(context: PipelineContext = PIPELINE_CONTEXT.ENTITY): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_FRAGMENTS_PENDING,
      context,
    });
    return get(
      context === PIPELINE_CONTEXT.ENTITY
        ? DataUrlConstants.ENTITY_PIPELINE_FRAGMENT
        : DataUrlConstants.FIELD_PIPELINE_FRAGMENT
    )
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_FRAGMENTS_FULFILLED,
          fragments: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_FRAGMENTS_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function saveFragment(
  fragment: FragmentModel,
  context: PipelineContext = PIPELINE_CONTEXT.ENTITY
): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.SAVE_FRAGMENT_PENDING,
      fragment,
      context,
    });

    return post(
      context === PIPELINE_CONTEXT.ENTITY
        ? DataUrlConstants.ENTITY_PIPELINE_FRAGMENT
        : DataUrlConstants.FIELD_PIPELINE_FRAGMENT,
      fragment
    )
      .then((resp) => {
        dispatch({
          type: ActionTypes.SAVE_FRAGMENT_FULFILLED,
          payload: resp.data,
        });
        dispatch(getFragments(context));
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.SAVE_FRAGMENT_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function deleteFragment(
  fragmentId: string,
  context: PipelineContext = PIPELINE_CONTEXT.ENTITY
): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.DELETE_FRAGMENT_PENDING,
      fragmentId,
      context,
    });
    return deleteRequest(
      replaceToken(
        context === PIPELINE_CONTEXT.ENTITY
          ? DataUrlConstants.ENTITY_PIPELINE_FRAGMENT_ITEM
          : DataUrlConstants.FIELD_PIPELINE_FRAGMENT_ITEM,
        { fragmentId }
      )
    )
      .then((resp) => {
        dispatch({
          type: ActionTypes.DELETE_FRAGMENT_FULFILLED,
        });
        dispatch(getFragments(context));
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.DELETE_FRAGMENT_FAILED,
          error: getErrorMessage(error),
        });
      });
  };
}

export function hideFragment(
  fragmentId: string,
  context: PipelineContext = PIPELINE_CONTEXT.ENTITY
): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.HIDE_FRAGMENT_PENDING,
      fragmentId,
      context,
    });
    return post(
      replaceToken(
        context === PIPELINE_CONTEXT.ENTITY
          ? DataUrlConstants.ENTITY_PIPELINE_FRAGMENT_HIDE
          : DataUrlConstants.FIELD_PIPELINE_FRAGMENT_HIDE,
        { fragmentId }
      )
    )
      .then((resp) => {
        dispatch({
          type: ActionTypes.HIDE_FRAGMENT_FULFILLED,
          fragmentId,
          context,
        });
        dispatch(getFragments(context));
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.HIDE_FRAGMENT_FAILED,
          error: getErrorMessage(error),
          fragmentId,
          context,
        });
      });
  };
}

export function showFragment(
  fragmentId: string,
  context: PipelineContext = PIPELINE_CONTEXT.ENTITY
): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.SHOW_FRAGMENT_PENDING,
      fragmentId,
      context,
    });
    return post(
      replaceToken(
        context === PIPELINE_CONTEXT.ENTITY
          ? DataUrlConstants.ENTITY_PIPELINE_FRAGMENT_SHOW
          : DataUrlConstants.FIELD_PIPELINE_FRAGMENT_SHOW,
        { fragmentId }
      )
    )
      .then((resp) => {
        dispatch({
          type: ActionTypes.SHOW_FRAGMENT_FULFILLED,
          fragmentId,
          context,
        });
        dispatch(getFragments(context));
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.SHOW_FRAGMENT_FAILED,
          error: getErrorMessage(error),
          fragmentId,
          context,
        });
      });
  };
}

export function shareFragment(
  fragmentId: string,
  instanceIds: InstanceIdsModel,
  context: PipelineContext = PIPELINE_CONTEXT.ENTITY
): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.SHARE_FRAGMENT_PENDING,
      fragmentId,
      instanceIds,
      context,
    });

    return put(
      replaceToken(
        context === PIPELINE_CONTEXT.ENTITY
          ? DataUrlConstants.ENTITY_PIPELINE_FRAGMENT_SHARE
          : DataUrlConstants.FIELD_PIPELINE_FRAGMENT_SHARE,
        { fragmentId }
      ),
      instanceIds
    )
      .then((resp) => {
        dispatch({
          type: ActionTypes.SHARE_FRAGMENT_FULFILLED,
          fragmentId,
          instanceIds,
          context,
        });
        dispatch(getFragments(context));
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.SHARE_FRAGMENT_FAILED,
          error: getErrorMessage(error),
          fragmentId,
          instanceIds,
          context,
        });
      });
  };
}

export function getFragmentShares(
  fragmentId: string,
  context: PipelineContext = PIPELINE_CONTEXT.ENTITY
): PromiseThunkAction {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_FRAGMENT_SHARES_PENDING,
      fragmentId,
      context,
    });

    return get(
      replaceToken(
        context === PIPELINE_CONTEXT.ENTITY
          ? DataUrlConstants.ENTITY_PIPELINE_FRAGMENT_SHARE
          : DataUrlConstants.FIELD_PIPELINE_FRAGMENT_SHARE,
        { fragmentId }
      )
    ).then((resp) => {
      dispatch({
        type: ActionTypes.GET_FRAGMENT_SHARES_FULFILLED,
        fragmentShares: resp.data,
        fragmentId,
      });
    });
  };
}
