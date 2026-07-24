//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { message } from 'antd';

import { SyncariThunkDispatch } from 'hooks/redux';
import { deleteRequest, get, post, put, redirectToLogin } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { replaceToken } from 'utils/UrlUtil';

const tn = tNamespaced('Settings.SubProfile');
// Action types that are dispatched by this action
const ActionTypes = {
  GET_SUBSCRIPTIONS: 'GET_SUBSCRIPTIONS',
  GET_SUBSCRIPTIONS_PENDING: 'GET_SUBSCRIPTIONS_PENDING',
  GET_SUBSCRIPTIONS_FULFILLED: 'GET_SUBSCRIPTIONS_FULFILLED',
  GET_SUBSCRIPTIONS_FAILED: 'GET_SUBSCRIPTIONS_FAILED',

  SHOW_SUBSCRIPTION_MODAL: 'SHOW_SUBSCRIPTION_MODAL',

  DELETE_SUBSCRIPTION_PENDING: 'DELETE_SUBSCRIPTION_PENDING',
  DELETE_SUBSCRIPTION_FULFILLED: 'DELETE_SUBSCRIPTION_FULFILLED',
  DELETE_SUBSCRIPTION_FAILED: 'DELETE_SUBSCRIPTION_FAILED',

  INVITE_USER_PENDING: 'INVITE_USER_PENDING',
  INVITE_USER_FULFILLED: 'INVITE_USER_FULFILLED',
  INVITE_USER_FAILED: 'INVITE_USER_FAILED',

  UPDATE_PROFILE_PENDING: 'UPDATE_PROFILE_PENDING',
  UPDATE_PROFILE_FULFILLED: 'UPDATE_PROFILE_FULFILLED',
  UPDATE_PROFILE_FAILED: 'UPDATE_PROFILE_FAILED',
};

/**
 * Get the list of connectors that are available
 */
export function getSubscriptions() {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.GET_SUBSCRIPTIONS_PENDING,
    });

    return get(DataUrlConstants.SUBSCRIPTION)
      .then((resp) => {
        dispatch({
          type: ActionTypes.GET_SUBSCRIPTIONS_FULFILLED,
          subscriptions: resp.data,
        });
      })
      .catch((error) => {
        dispatch({
          type: ActionTypes.GET_SUBSCRIPTIONS_FAILED,
        });
        redirectToLogin();
      });
  };
}

export function createSubscription(params: any) {
  const {
    adminUserName,
    instanceName,
    instanceType,
    instanceDisplayName,
    organizationName,
    orgType,
    maxInstance,
    planName,
    adminFirstName,
    adminLastName,
  } = params;
  const payload = {
    adminUserName,
    instanceName,
    instanceType,
    instanceDisplayName,
    organizationName,
    orgType,
    maxInstance,
    planName,
    adminFirstName,
    adminLastName,
  };

  return (dispatch: SyncariThunkDispatch) => {
    return post(DataUrlConstants.SUBSCRIPTION, payload)
      .then((resp) => {
        getSubscriptions()(dispatch);
        return { success: true };
      })
      .catch((error) => {
        getSubscriptions()(dispatch);
        return {
          success: false,
          error,
        };
      })
      .finally(() => getSubscriptions()(dispatch));
  };
}

export function showSubscriptionModal(show = true) {
  return {
    type: ActionTypes.SHOW_SUBSCRIPTION_MODAL,
    visible: show,
  };
}

export function deleteSubscription(params: any) {
  return (dispatch: SyncariThunkDispatch) => {
    dispatch({
      type: ActionTypes.DELETE_SUBSCRIPTION_PENDING,
    });

    return deleteRequest(replaceToken(DataUrlConstants.DELETE_SUBSCRIPTION, { orgId: params }))
      .then((resp) => {
        dispatch({
          type: ActionTypes.DELETE_SUBSCRIPTION_FULFILLED,
          user: resp.data,
        });

        return { success: true };
      })
      .catch((resp) => {
        dispatch({
          type: ActionTypes.DELETE_SUBSCRIPTION_FAILED,
        });

        return { success: false };
      })
      .finally(() => getSubscriptions()(dispatch));
  };
}

export function updateProfile(params: any) {
  const { name, logo, id, type, maxInstance } = params;

  const payload = new FormData();
  payload.append('name', name);
  payload.append('id', id);
  payload.append('logo', logo);
  payload.append('maxInstance', maxInstance);
  payload.append('type', type);

  return () => {
    return put(DataUrlConstants.SUBSCRIPTION, payload)
      .then(() => {
        message.success(tn('profile_save_success'));
      })
      .catch(() => {
        message.error(tn('profile_save_error'));
      });
  };
}

export { ActionTypes };
