import produce, { Draft } from 'immer';

import AppConstants from 'utils/AppConstants';

import {
  OrganizationAction,
  OrganizationState,
  GET_ORGANIZATION_SSO_CONFIG_PENDING,
  GET_ORGANIZATION_SSO_CONFIG_FULFILLED,
  GET_ORGANIZATION_SSO_CONFIG_FAILED,
  UPDATE_ORGANIZATION_SSO_CONFIG_PENDING,
  UPDATE_ORGANIZATION_SSO_CONFIG_FULFILLED,
  UPDATE_ORGANIZATION_SSO_CONFIG_FAILED,
  DELETE_ORGANIZATION_SSO_CONFIG_PENDING,
  DELETE_ORGANIZATION_SSO_CONFIG_FULFILLED,
  DELETE_ORGANIZATION_SSO_CONFIG_FAILED,
} from './types';

const { FETCH_STATUS } = AppConstants;

const initialState: OrganizationState = {
  sso: {
    fetchStatus: FETCH_STATUS.IDLE,
    updateStatus: {},
    updateError: {},
    deleteStatus: {},
    deleteError: {},
    identityProviders: {},
  },
};

const reducer = produce((draft: Draft<OrganizationState>, action: OrganizationAction) => {
  switch (action.type) {
    case GET_ORGANIZATION_SSO_CONFIG_PENDING:
      draft.sso.fetchStatus = FETCH_STATUS.LOADING;
      break;
    case GET_ORGANIZATION_SSO_CONFIG_FULFILLED:
      draft.sso.fetchStatus = FETCH_STATUS.SUCCESS;
      draft.sso.identityProviders = action.payload.providers;
      break;
    case GET_ORGANIZATION_SSO_CONFIG_FAILED:
      draft.sso.fetchStatus = FETCH_STATUS.ERROR;
      break;
    case UPDATE_ORGANIZATION_SSO_CONFIG_PENDING:
      draft.sso.updateStatus[action.payload.provider] = FETCH_STATUS.LOADING;
      delete draft.sso.updateError[action.payload.provider];
      break;
    case UPDATE_ORGANIZATION_SSO_CONFIG_FULFILLED:
      draft.sso.updateStatus[action.payload.provider] = FETCH_STATUS.SUCCESS;
      draft.sso.identityProviders[action.payload.provider] = action.payload.config;
      break;
    case UPDATE_ORGANIZATION_SSO_CONFIG_FAILED:
      draft.sso.updateStatus[action.payload.provider] = FETCH_STATUS.ERROR;
      draft.sso.updateError[action.payload.provider] = action.payload.error;
      break;
    case DELETE_ORGANIZATION_SSO_CONFIG_PENDING:
      draft.sso.deleteStatus[action.payload.provider] = FETCH_STATUS.LOADING;
      break;
    case DELETE_ORGANIZATION_SSO_CONFIG_FULFILLED:
      draft.sso.deleteStatus[action.payload.provider] = FETCH_STATUS.SUCCESS;
      delete draft.sso.identityProviders[action.payload.provider];
      break;
    case DELETE_ORGANIZATION_SSO_CONFIG_FAILED:
      draft.sso.deleteStatus[action.payload.provider] = FETCH_STATUS.ERROR;
      draft.sso.deleteError[action.payload.provider] = action.payload.error;
      break;

    default:
      return draft;
  }
}, initialState);

export default reducer;
