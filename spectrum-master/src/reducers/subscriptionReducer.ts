// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { each } from 'lodash';

import { ActionTypes } from 'actions/subscriptionActions';
import AppConstants from 'utils/AppConstants';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';

function _getSubscriptions(data) {
  let subscriptions = [];
  if (!data) {
    subscriptions = [];
  } else {
    each(data, (subscription) => {
      subscriptions.push({
        key: subscription.id,
        id: subscription.id,
        name: subscription.name,
        instancesCount: subscription.instances.length,
        instances: subscription.instances,
        createdAt: subscription.createdAt,
        createdBy: subscription.createdBy,
        deletedAt: subscription.deletedAt,
        deletedBy: subscription.deletedBy,
        status: subscription.status,
      });
    });
  }
  return {
    subscriptions,
  };
}

export function _getDefaultState() {
  return {
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.SUBSCRIPTION),
    ..._getSubscriptions(),
    subscriptionModalVisible: false,
    planName: 'default',
    fetchingSubscriptions: false,
  };
}

export default function subscriptionReducer(state = _getDefaultState(), action) {
  switch (action.type) {
    case ActionTypes.GET_SUBSCRIPTIONS_PENDING:
      return {
        ...state,
        fetchingSubscriptions: true,
      };
    case ActionTypes.GET_SUBSCRIPTIONS_FULFILLED:
      return {
        ...state,
        ..._getSubscriptions(action.subscriptions),
        fetchingSubscriptions: false,
      };
    case ActionTypes.GET_SUBSCRIPTIONS_FAILED:
      return {
        ...state,
        fetchingSubscriptions: false,
      };
    case ActionTypes.SHOW_SUBSCRIPTION_MODAL:
      return {
        ...state,
        subscriptionModalVisible: action.visible,
      };
    default:
      return state;
  }
}
